package br.com.srm.creditengine.liquidacao

import br.com.srm.creditengine.cadastro.adaptador.saida.RecebiveisJdbc
import br.com.srm.creditengine.liquidacao.adaptador.saida.LiquidacaoJdbc
import br.com.srm.creditengine.liquidacao.adaptador.saida.MetricasLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.ServicoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ComandoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdempotenciaLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.TransacaoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.liquidacao.dominio.RecebivelNaoEncontrado
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoCambialResiliente
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager
import io.micrometer.core.instrument.simple.SimpleMeterRegistry

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiquidacaoPostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var gerenciador: PlatformTransactionManager
    @Autowired private lateinit var historico: HistoricoCambialResiliente

    private fun fixture(): Fixture {
        val instante = Instant.parse("2040-01-01T00:00:00Z").plusSeconds(sequencia.incrementAndGet() * 86400L)
        val clock = Clock.fixed(instante, ZoneOffset.UTC)
        val org = UUID.randomUUID()
        val usuario = UUID.randomUUID()
        val cedente = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'Teste B06')", org)
        jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash) VALUES (?, ?, ?, 'hash')",
            usuario, org, "$usuario@example.test")
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'Teste', 'teste')",
            cedente, org)
        val taxas = ManterEConsultarTaxas(historico, SeletorTaxas(Duration.ofMinutes(30)), clock)
        val base = taxas.registrarTaxaBase(BigDecimal("0.01"), instante)
        val dup = taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), instante)
        taxas.registrarSpread(Modalidades.CHEQUE_PRE_DATADO, BigDecimal("0.025"), instante)
        val cotacao = taxas.registrarCotacao(BigDecimal("5.4321"), instante)
        return Fixture(org, usuario, cedente, instante, clock, taxas, base.id, dup.id, cotacao.id)
    }

    private inner class Fixture(
        val org: UUID, val usuario: UUID, val cedente: UUID, val instante: Instant, val clock: Clock,
        val taxas: ManterEConsultarTaxas, val base: UUID, val spread: UUID, val cotacao: UUID,
    ) {
        val repositorio = LiquidacaoJdbc(jdbc, TransactionTemplate(gerenciador), clock)
        fun servico(
            id: IdentidadeLiquidacao = IdentidadeLiquidacao(org, usuario),
            idempotencia: IdempotenciaLiquidacao = repositorio,
            transacao: TransacaoLiquidacao = repositorio,
            relogio: Clock = clock,
        ) = ServicoLiquidacao(IdentidadeConfiavel { id }, RecebiveisJdbc(jdbc), idempotencia, taxas, transacao, relogio)

        fun recebivel(modalidade: String = Modalidades.DUPLICATA_MERCANTIL, face: String = "100000.00", prazo: Int = 3): UUID {
            val id = UUID.randomUUID()
            jdbc.update("""INSERT INTO recebiveis
                (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
                VALUES (?, ?, ?, ?, ?, ?)""", id, org, cedente, modalidade, BigDecimal(face), prazo)
            return id
        }

        fun contar(tabela: String): Int =
            jdbc.queryForObject("SELECT count(*) FROM $tabela WHERE organizacao_id = ?", Int::class.java, org)!!
    }

    @Test
    fun `golden cases e snapshot persistente com replay independente das taxas posteriores`() {
        val f = fixture()
        val c1 = f.servico().liquidar(ComandoLiquidacao(f.recebivel(), MoedaPagamento.BRL, "c1"))
        val c2 = f.servico().liquidar(ComandoLiquidacao(
            f.recebivel(Modalidades.CHEQUE_PRE_DATADO, "25000.00", 2), MoedaPagamento.BRL, "c2",
        ))
        val comando = ComandoLiquidacao(f.recebivel(), MoedaPagamento.USD, "c3")
        assertThrows<IllegalArgumentException> { comando.copy(chaveIdempotencia = " ") }
        val c3 = f.servico().liquidar(comando)
        assertThat(c1.valorPresenteBrl).isEqualByComparingTo("92859.94")
        assertThat(c1.desagioBrl).isEqualByComparingTo("7140.06")
        assertThat(c2.valorPagamento).isEqualByComparingTo("23337.77")
        assertThat(c2.desagioBrl).isEqualByComparingTo("1662.23")
        assertThat(c3.valorPresenteBrl).isEqualByComparingTo("92859.94")
        assertThat(c3.valorPagamento).isEqualByComparingTo("17094.67")
        assertThat(c3.desagioBrl).isEqualByComparingTo("7140.06")
        val snapshot = jdbc.queryForMap("SELECT * FROM liquidacoes WHERE id = ?", c3.liquidacaoId)
        assertThat(snapshot["taxa_base_id"]).isEqualTo(f.base)
        assertThat(snapshot["spread_id"]).isEqualTo(f.spread)
        assertThat(snapshot["cotacao_cambial_id"]).isEqualTo(f.cotacao)
        assertThat(snapshot["versao_recebivel"]).isEqualTo(1L)
        assertThat(snapshot["organizacao_id"]).isEqualTo(f.org)
        assertThat(snapshot["recebivel_id"]).isEqualTo(comando.recebivelId)
        assertThat(snapshot["modalidade"]).isEqualTo(Modalidades.DUPLICATA_MERCANTIL)
        assertThat(snapshot["valor_face_brl"] as BigDecimal).isEqualByComparingTo("100000.00")
        assertThat(snapshot["prazo_meses"]).isEqualTo(3)
        assertThat(snapshot["taxa_base_mensal"] as BigDecimal).isEqualByComparingTo("0.01")
        assertThat(snapshot["spread_mensal"] as BigDecimal).isEqualByComparingTo("0.015")
        assertThat(snapshot["taxa_brl_por_unidade"] as BigDecimal).isEqualByComparingTo("5.4321")
        assertThat((snapshot["liquidada_em"] as java.sql.Timestamp).toInstant()).isEqualTo(f.instante)
        assertThat((snapshot["taxa_base_vigente_em"] as java.sql.Timestamp).toInstant()).isEqualTo(f.instante)
        assertThat((snapshot["spread_vigente_em"] as java.sql.Timestamp).toInstant()).isEqualTo(f.instante)
        assertThat((snapshot["cotacao_vigente_em"] as java.sql.Timestamp).toInstant()).isEqualTo(f.instante)
        val validade = jdbc.queryForObject("SELECT cotacao_valida_ate FROM liquidacoes WHERE id = ?",
            OffsetDateTime::class.java, c3.liquidacaoId)
        assertThat(validade?.toInstant()).isEqualTo(f.instante.plusSeconds(1800))
        val replay = jdbc.queryForMap("""SELECT resposta_http, resposta_corpo->>'valorPagamento' AS valor,
            resposta_corpo->>'moeda' AS moeda FROM idempotencia WHERE liquidacao_id = ?""", c3.liquidacaoId)
        assertThat((replay["resposta_http"] as Number).toInt()).isEqualTo(201)
        assertThat(replay["valor"]).isEqualTo("17094.67")
        assertThat(replay["moeda"]).isEqualTo("USD")
        assertThrows<DataAccessException> {
            jdbc.update("UPDATE liquidacoes SET valor_pagamento = 1 WHERE id = ?", c3.liquidacaoId)
        }
        assertThat(jdbc.queryForObject("SELECT versao FROM recebiveis WHERE id = ?", Long::class.java, comando.recebivelId)).isEqualTo(1L)
        assertThat(f.contar("liquidacoes")).isEqualTo(3)
        assertThat(f.contar("idempotencia")).isEqualTo(3)
        assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(3)
        f.taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.07"), f.instante.plusSeconds(1))
        assertThat(f.servico().liquidar(comando)).isEqualTo(c3)
        assertThat(f.servico(relogio = Clock.fixed(f.instante.plusSeconds(3600), ZoneOffset.UTC)).liquidar(comando)).isEqualTo(c3)
        assertThrows<ConflitoLiquidacao> { f.servico().liquidar(comando.copy(moeda = MoedaPagamento.BRL)) }
        assertThrows<ConflitoLiquidacao> { f.servico().liquidar(comando.copy(chaveIdempotencia = "outra")) }
    }

    @Test
    fun `utiliza spread historico e impede acesso cruzado`() {
        val f = fixture()
        val futuro = f.instante.plusSeconds(60)
        f.taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.05"), futuro)
        val clock = Clock.fixed(futuro, ZoneOffset.UTC)
        val comando = ComandoLiquidacao(f.recebivel(), MoedaPagamento.BRL, "spread")
        val outro = fixture()
        assertThrows<RecebivelNaoEncontrado> { outro.servico().liquidar(comando) }
        val resultado = f.servico(relogio = clock).liquidar(comando)
        assertThat(resultado.valorPresenteBrl).isEqualByComparingTo("83961.93")
        assertThat(jdbc.queryForObject("SELECT spread_mensal FROM liquidacoes WHERE id = ?", BigDecimal::class.java, resultado.liquidacaoId))
            .isEqualByComparingTo("0.05")
    }

    @Test
    fun `auditoria invalida reverte recebivel reserva e liquidacao`() {
        val f = fixture()
        val id = f.recebivel()
        val comando = ComandoLiquidacao(id, MoedaPagamento.BRL, "rollback")
        assertThrows<DataAccessException> {
            f.servico(IdentidadeLiquidacao(f.org, UUID.randomUUID())).liquidar(comando)
        }
        assertThat(f.contar("liquidacoes")).isZero()
        assertThat(f.contar("idempotencia")).isZero()
        assertThat(f.contar("auditoria_liquidacoes")).isZero()
        val estado = jdbc.queryForMap("SELECT status, versao FROM recebiveis WHERE id = ?", id)
        assertThat(estado["status"]).isEqualTo("DISPONIVEL")
        assertThat(estado["versao"]).isEqualTo(0L)
        assertThat(f.servico().liquidar(comando).recebivelId).isEqualTo(id)
    }

    @Test
    fun `contabiliza e registra confirmacao somente apos commit e nao no replay ou rollback`() {
        val f = fixture()
        val registro = SimpleMeterRegistry()
        val metricas = MetricasLiquidacao(registro)
        val persistencia = LiquidacaoJdbc(
            jdbc,
            TransactionTemplate(gerenciador),
            f.clock,
            aoConfirmarAposCommit = { _, liquidacao ->
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
                assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM liquidacoes WHERE recebivel_id = ?",
                        Int::class.java,
                        liquidacao.recebivel.id,
                    ),
                ).isEqualTo(1)
                metricas.registrarConfirmacao()
            },
            aoReproduzir = metricas::registrarReplay,
        )
        val recebivelId = f.recebivel()
        val comando = ComandoLiquidacao(recebivelId, MoedaPagamento.BRL, "metricas")
        val servico = f.servico(idempotencia = persistencia, transacao = persistencia)

        servico.liquidar(comando)
        assertThat(registro.get("srm.liquidacoes.confirmadas").counter().count()).isEqualTo(1.0)
        servico.liquidar(comando)
        assertThat(registro.get("srm.liquidacoes.confirmadas").counter().count()).isEqualTo(1.0)
        assertThat(registro.get("srm.liquidacoes.replays").counter().count()).isEqualTo(1.0)

        val outroRecebivel = f.recebivel()
        assertThrows<DataAccessException> {
            f.servico(
                id = IdentidadeLiquidacao(f.org, UUID.randomUUID()),
                idempotencia = persistencia,
                transacao = persistencia,
            ).liquidar(ComandoLiquidacao(outroRecebivel, MoedaPagamento.BRL, "rollback-metrica"))
        }
        assertThat(registro.get("srm.liquidacoes.confirmadas").counter().count()).isEqualTo(1.0)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM liquidacoes WHERE recebivel_id = ?", Int::class.java, outroRecebivel))
            .isZero()
    }

    @Test
    fun `cotacao vencida antes da transacao nao deixa efeitos`() {
        val f = fixture()
        val id = f.recebivel()
        val repositorioAtrasado = LiquidacaoJdbc(
            jdbc, TransactionTemplate(gerenciador), Clock.fixed(f.instante.plusSeconds(1801), ZoneOffset.UTC),
        )
        assertThrows<DadosFinanceirosIndisponiveis> {
            f.servico(transacao = repositorioAtrasado).liquidar(ComandoLiquidacao(id, MoedaPagamento.USD, "vencida"))
        }
        assertThat(f.contar("liquidacoes")).isZero()
        assertThat(f.contar("idempotencia")).isZero()
        assertThat(f.contar("auditoria_liquidacoes")).isZero()
    }

    @Test
    fun `cotacao ausente futura ou vencida nao deixa efeitos`() {
        val f = fixture()
        val id = f.recebivel()
        val comando = ComandoLiquidacao(id, MoedaPagamento.USD, "sem-cambio")
        val corte = Instant.parse("2030-01-01T00:00:00Z")
        val clock = Clock.fixed(corte, ZoneOffset.UTC)
        val taxasAntigas = ManterEConsultarTaxas(
            HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), clock,
        )
        taxasAntigas.registrarTaxaBase(BigDecimal("0.01"), corte)
        taxasAntigas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), corte)
        assertThrows<DadosFinanceirosIndisponiveis> { f.servico(relogio = clock).liquidar(comando) }
        taxasAntigas.registrarCotacao(BigDecimal("5.4321"), corte.plusSeconds(10))
        assertThrows<DadosFinanceirosIndisponiveis> { f.servico(relogio = clock).liquidar(comando) }
        val expirado = Clock.fixed(corte.plusSeconds(1811), ZoneOffset.UTC)
        assertThrows<DadosFinanceirosIndisponiveis> { f.servico(relogio = expirado).liquidar(comando) }
        assertThat(f.contar("liquidacoes")).isZero()
        assertThat(f.contar("idempotencia")).isZero()
        assertThat(f.contar("auditoria_liquidacoes")).isZero()
    }

    @Test
    fun `vencimento depois da atualizacao otimista reverte a transacao`() {
        val f = fixture()
        val id = f.recebivel()
        var leituras = 0
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = if (leituras++ == 0) f.instante else f.instante.plusSeconds(1801)
        }
        val persistencia = LiquidacaoJdbc(jdbc, TransactionTemplate(gerenciador), clock)
        assertThrows<DadosFinanceirosIndisponiveis> {
            f.servico(transacao = persistencia).liquidar(ComandoLiquidacao(id, MoedaPagamento.USD, "atrasada"))
        }
        val estado = jdbc.queryForMap("SELECT status, versao FROM recebiveis WHERE id = ?", id)
        assertThat(estado["status"]).isEqualTo("DISPONIVEL")
        assertThat(estado["versao"]).isEqualTo(0L)
        assertThat(f.contar("liquidacoes")).isZero()
        assertThat(f.contar("idempotencia")).isZero()
        assertThat(f.contar("auditoria_liquidacoes")).isZero()
    }

    @Test
    fun `requisicoes simultaneas com a mesma chave devolvem a mesma resposta`() {
        val f = fixture()
        val id = f.recebivel()
        val barreira = CountDownLatch(2)
        val coordenada = TransacaoLiquidacao { identidade, chave, hash, liquidacao ->
            barreira.countDown()
            check(barreira.await(10, TimeUnit.SECONDS))
            f.repositorio.confirmar(identidade, chave, hash, liquidacao)
        }

        val comando = ComandoLiquidacao(id, MoedaPagamento.USD, "replay")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tarefas = (1..2).map {
                executor.submit<br.com.srm.creditengine.liquidacao.dominio.ResultadoLiquidacao> {
                    f.servico(transacao = coordenada).liquidar(comando)
                }
            }
            val resultados = tarefas.map { it.get(20, TimeUnit.SECONDS) }
            assertThat(resultados[0]).isEqualTo(resultados[1])
            assertThat(f.contar("liquidacoes")).isEqualTo(1)
            assertThat(f.contar("idempotencia")).isEqualTo(1)
            assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `comandos diferentes simultaneos com a mesma chave geram conflito`() {
        val f = fixture()
        val id = f.recebivel()
        val barreira = CountDownLatch(2)
        val coordenada = TransacaoLiquidacao { identidade, chave, hash, liquidacao ->
            barreira.countDown()
            check(barreira.await(10, TimeUnit.SECONDS))
            f.repositorio.confirmar(identidade, chave, hash, liquidacao)
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tarefas = MoedaPagamento.entries.map { moeda ->
                executor.submit<Result<UUID>> {
                    runCatching { f.servico(transacao = coordenada).liquidar(ComandoLiquidacao(id, moeda, "mesma")).liquidacaoId }
                }
            }
            val resultados = tarefas.map { it.get(20, TimeUnit.SECONDS) }
            assertThat(resultados.count { it.isSuccess }).isEqualTo(1)
            assertThat(resultados.single { it.isFailure }.exceptionOrNull()).isInstanceOf(ConflitoLiquidacao::class.java)
            assertThat(f.contar("liquidacoes")).isEqualTo(1)
            assertThat(f.contar("idempotencia")).isEqualTo(1)
            assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `duas chaves concorrentes para o mesmo recebivel confirmam somente uma`() {
        val f = fixture()
        val id = f.recebivel()
        val barreira = CountDownLatch(2)
        val coordenada = TransacaoLiquidacao { identidade, chave, hash, liquidacao ->
            barreira.countDown()
            check(barreira.await(10, TimeUnit.SECONDS))
            f.repositorio.confirmar(identidade, chave, hash, liquidacao)
        }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tarefas = listOf("a", "b").map { chave ->
                executor.submit<Result<UUID>> {
                    runCatching { f.servico(transacao = coordenada).liquidar(ComandoLiquidacao(id, MoedaPagamento.BRL, chave)).liquidacaoId }
                }
            }
            val resultados = tarefas.map { it.get(20, TimeUnit.SECONDS) }
            assertThat(resultados.count { it.isSuccess }).isEqualTo(1)
            assertThat(resultados.single { it.isFailure }.exceptionOrNull()).isInstanceOf(ConflitoLiquidacao::class.java)
            assertThat(f.contar("liquidacoes")).isEqualTo(1)
            assertThat(f.contar("idempotencia")).isEqualTo(1)
            assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(1)
        } finally {
            executor.shutdownNow()
        }
    }

    companion object {
        private val sequencia = AtomicInteger()
    }
}
