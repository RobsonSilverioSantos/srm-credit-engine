package br.com.srm.creditengine.taxas

import br.com.srm.creditengine.cadastro.adaptador.saida.RecebiveisJdbc
import br.com.srm.creditengine.liquidacao.adaptador.saida.LiquidacaoJdbc
import br.com.srm.creditengine.liquidacao.aplicacao.ServicoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ComandoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoCambioJdbc
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.adaptador.saida.CoordenacaoPtaxJdbc
import br.com.srm.creditengine.taxas.adaptador.saida.ConsultaCambialIndisponivel
import br.com.srm.creditengine.taxas.aplicacao.BoletimPtax
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.ConsultarCambio
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.dominio.FonteCambial
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
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

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CambioPostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var gerenciador: PlatformTransactionManager
    private val modalidade = Modalidades.DUPLICATA_MERCANTIL

    private inner class Fixture(referenciaInicial: Instant? = null) {
        val referencia = referenciaInicial ?:
            Instant.parse("2090-01-06T16:00:00Z").plusSeconds(sequencia.incrementAndGet() * 864000L)
        var agora = referencia
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = agora
        }
        val org = UUID.randomUUID()
        val usuario = UUID.randomUUID()
        val cedente = UUID.randomUUID()
        val autor = IdentidadeUsuario(usuario, org)
        val transacao = TransactionTemplate(gerenciador)
        val historico = HistoricoCambioJdbc(jdbc, transacao, clock)
        val taxas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), clock,
            ConsultarCambio { historico.consultar(org, it) })
        val liquidacao = LiquidacaoJdbc(jdbc, transacao, clock)
        val servico = ServicoLiquidacao(IdentidadeConfiavel { IdentidadeLiquidacao(org, usuario) },
            RecebiveisJdbc(jdbc), liquidacao, taxas, liquidacao, clock)

        init {
            jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'B15')", org)
            jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash, perfil) VALUES (?, ?, ?, 'hash', 'MASTER')",
                usuario, org, "$usuario@example.invalid")
            jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'B15', 'legado')", cedente, org)
            taxas.registrarTaxaBase(BigDecimal("0.01"), referencia.minusSeconds(1))
            taxas.registrarSpread(modalidade, BigDecimal("0.015"), referencia.minusSeconds(1))
        }

        fun boletim(venda: String = "5.8889") = BoletimPtax(BigDecimal(venda).subtract(BigDecimal("0.0006")),
            BigDecimal(venda), referencia, referencia.atZone(FUSO).toLocalDate(),
            referencia.atZone(FUSO).toLocalDateTime().toString().replace('T', ' '))
        fun ptax(venda: String = "5.8889") = historico.sincronizar(listOf(boletim(venda)))
        fun recebivel(): UUID = UUID.randomUUID().also {
            jdbc.update("""INSERT INTO recebiveis (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
                VALUES (?, ?, ?, ?, 100000, 3)""", it, org, cedente, modalidade)
        }
        fun comando(id: UUID = recebivel(), chave: String = UUID.randomUUID().toString()): ComandoLiquidacao =
            RecebiveisJdbc(jdbc).buscar(org, id)!!.let { r ->
                val snapshot = taxas.consultar(modalidade, true)
                val preco = br.com.srm.creditengine.precificacao.aplicacao.precificarComTaxas(
                    r.modalidade, r.valorFaceBrl, r.prazoMeses, snapshot)
                ComandoLiquidacao(id, MoedaPagamento.USD, chave,
                    br.com.srm.creditengine.precificacao.aplicacao.RevisaoFinanceira.calcular(
                        r.modalidade, r.valorFaceBrl, r.prazoMeses, MoedaPagamento.USD, snapshot, preco))
            }
        fun contar(tabela: String): Int =
            jdbc.queryForObject("SELECT count(*) FROM $tabela WHERE organizacao_id = ?", Int::class.java, org)!!
    }

    @Test
    fun `PostgreSQL aceita 96h exatas mas rejeita 96h mais 1ms sem extensao em feriado prolongado`() {
        val f = Fixture(Instant.parse("2091-04-20T16:00:00Z"))
        f.ptax()
        val primeiro = f.historico.consultar(f.org, f.referencia)
        for (horas in listOf(24L, 48L, 72L, 96L)) {
            f.agora = f.referencia.plus(Duration.ofHours(horas))
            assertThat(f.historico.consultar(f.org, f.agora).cotacao.id).isEqualTo(primeiro.cotacao.id)
        }
        assertThat(f.referencia.atZone(FUSO).dayOfWeek).isEqualTo(java.time.DayOfWeek.FRIDAY)
        assertThat(f.agora.atZone(FUSO).dayOfWeek).isEqualTo(java.time.DayOfWeek.TUESDAY)
        f.agora = f.referencia.plus(Duration.ofHours(96)).plusMillis(1)
        assertThrows<DadosFinanceirosIndisponiveis> { f.historico.consultar(f.org, f.agora) }
        assertThat(f.ptax()).isZero()
        assertThrows<DadosFinanceirosIndisponiveis> { f.historico.consultar(f.org, f.agora) }
        f.agora = f.referencia.plus(Duration.ofDays(7))
        assertThrows<DadosFinanceirosIndisponiveis> { f.historico.consultar(f.org, f.agora) }
        assertThat(f.taxas.consultar(modalidade, false).cotacao).isNull()
    }

    @Test
    fun `primeira observacao e revisao tardia impedem conhecimento retroativo e reimportacao nao renova idade`() {
        val f = Fixture()
        f.agora = f.referencia.plusSeconds(60)
        f.ptax()
        assertThrows<DadosFinanceirosIndisponiveis> { f.historico.consultar(f.org, f.referencia) }
        val primeira = f.historico.consultar(f.org, f.agora)
        f.agora = f.referencia.plusSeconds(120)
        assertThat(f.ptax()).isZero()
        f.ptax("6.1000")
        val revisada = f.historico.consultar(f.org, f.agora)
        assertThat(revisada.cotacao.versao).isEqualTo(2)
        assertThat(revisada.cotacao.id).isNotEqualTo(primeira.cotacao.id)
        assertThat(revisada.cotacao.vigenteEm).isEqualTo(f.referencia)
        assertThat(f.historico.consultar(f.org, f.referencia.plusSeconds(90)).cotacao.id).isEqualTo(primeira.cotacao.id)
        assertThat(f.historico.consultar(f.org, f.referencia.plusSeconds(90)).cotacao.criadoEm)
            .isEqualTo(f.referencia.plusSeconds(60))
        assertThrows<IllegalArgumentException> { f.historico.sincronizar(listOf(f.boletim().copy(referencia = f.agora.plusSeconds(1)))) }
    }

    @Test
    fun `manual cadastrada nao ativa autoriza desde corte ate meia noite exclusiva e nao cai para PTAX ou DEMO`() {
        val f = Fixture()
        f.ptax()
        val ptax = f.historico.consultar(f.org, f.agora)
        f.taxas.registrarCotacao(BigDecimal("5.4321"), f.referencia)
        val manual = f.historico.registrarManual(f.autor, BigDecimal("6.0001"), "Contingência aprovada")
        assertThat(f.historico.consultar(f.org, f.agora).cotacao.id).isEqualTo(ptax.cotacao.id)
        f.agora = f.referencia.atZone(FUSO).toLocalDate().atTime(23, 59).atZone(FUSO).toInstant()
        val selecao = f.historico.selecionar(f.autor, FonteCambial.MANUAL, manual.id, "Autorizar")
        val autorizada = f.historico.consultar(f.org, f.agora)
        assertThat(autorizada.validaDesde).isEqualTo(selecao.autorizadoEm)
        assertThat(autorizada.cotacao.id).isEqualTo(manual.id)
        assertThat(f.historico.consultar(f.org, f.agora.minusNanos(1000)).cotacao.id).isEqualTo(ptax.cotacao.id)
        val fim = checkNotNull(selecao.validaAte)
        assertThat(f.historico.consultar(f.org, fim.minusNanos(1000)).cotacao.id).isEqualTo(manual.id)
        assertThat(fim.atZone(FUSO).toLocalTime().toString()).isEqualTo("00:00")
        assertThrows<DadosFinanceirosIndisponiveis> { f.historico.consultar(f.org, fim) }
        f.agora = fim
        assertThrows<DadosFinanceirosIndisponiveis> { f.taxas.consultar(modalidade, true) }
        assertThrows<ConflitoLiquidacao> { f.historico.selecionar(f.autor, FonteCambial.MANUAL, manual.id, "Reativar") }
        f.historico.selecionar(f.autor, FonteCambial.BCB_PTAX, null, "Voltar explicitamente")
        assertThat(f.historico.consultar(f.org, f.agora).cotacao.fonte).isEqualTo(FonteCambial.BCB_PTAX)
    }

    @Test
    fun `snapshot auditavel replay apos expiracao e conflito de revisao nao reprecificam nem gravam parcialmente`() {
        val f = Fixture()
        f.ptax()
        val comando = f.comando()
        val resultado = f.servico.liquidar(comando)
        assertThat(resultado.liquidacao.valorPagamento).isEqualByComparingTo("15768.64")
        val snapshot = jdbc.queryForMap(
            "SELECT cambio_snapshot::text AS dados, politica_cambial_id FROM liquidacoes WHERE id = ?",
            resultado.liquidacao.liquidacaoId)
        assertThat(snapshot["dados"].toString()).contains("BCB_PTAX", "VENDA", "FECHAMENTO", "2090", "America/Sao_Paulo")
        assertThat(snapshot["politica_cambial_id"]).isNotNull()
        val obsoleto = f.comando()
        f.agora = f.agora.plusSeconds(1)
        f.ptax("6.1000")
        assertThrows<ConflitoLiquidacao> { f.servico.liquidar(obsoleto) }
        f.agora = f.referencia.plus(Duration.ofHours(97))
        assertThat(f.servico.liquidar(comando)).isEqualTo(resultado)
        assertThrows<ConflitoLiquidacao> { f.servico.liquidar(comando.copy(revisaoCambial = "0".repeat(64))) }
        assertThat(f.contar("liquidacoes")).isEqualTo(1)
        assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(1)
        assertThat(f.contar("idempotencia")).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT status FROM recebiveis WHERE id = ?", String::class.java, obsoleto.recebivelId))
            .isEqualTo("DISPONIVEL")
    }

    @Test
    fun `importacoes simultaneas sao idempotentes e preservam historico imutavel`() {
        val f = Fixture()
        val barreira = CountDownLatch(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val tarefas = (1..2).map {
                pool.submit<Int> {
                    barreira.countDown()
                    check(barreira.await(10, TimeUnit.SECONDS))
                    f.ptax()
                }
            }
            assertThat(tarefas.sumOf { it.get(15, TimeUnit.SECONDS) }).isEqualTo(1)
        } finally { pool.shutdownNow() }
        val id = f.historico.consultar(f.org, f.agora).cotacao.id
        assertThrows<DataAccessException> { jdbc.update("UPDATE cotacoes_cambiais SET versao = 99 WHERE id = ?", id) }
        assertThrows<DataAccessException> { jdbc.update("DELETE FROM cotacoes_cambiais WHERE id = ?", id) }
    }

    @Test
    fun `duas liquidacoes PTAX concorrentes geram um commit um conflito e mesma chave permite replay`() {
        val f = Fixture()
        f.ptax()
        val original = f.comando()
        val barreira = CountDownLatch(2)
        val taxasConcorrentes = object : TaxasPorta by f.taxas {
            override fun consultarEm(modalidade: String, exigirCotacao: Boolean, referencia: Instant): TaxasParaSnapshot =
                f.taxas.consultarEm(modalidade, exigirCotacao, referencia).also {
                    // Ambos já leram a mesma versão DISPONIVEL antes de disputar o UPDATE SQL.
                    barreira.countDown()
                    check(barreira.await(10, TimeUnit.SECONDS))
                }
        }
        val concorrente = ServicoLiquidacao(IdentidadeConfiavel { IdentidadeLiquidacao(f.org, f.usuario) },
            RecebiveisJdbc(jdbc), f.liquidacao, taxasConcorrentes, f.liquidacao, f.clock)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val tarefas = (1..2).map { n ->
                pool.submit<String> {
                    try {
                        concorrente.liquidar(original.copy(chaveIdempotencia = "chave-$n"))
                        "commit"
                    } catch (_: ConflitoLiquidacao) { "conflito" }
                }
            }
            assertThat(tarefas.map { it.get(20, TimeUnit.SECONDS) }).containsExactlyInAnyOrder("commit", "conflito")
        } finally { pool.shutdownNow() }
        assertThat(f.contar("liquidacoes")).isEqualTo(1)
        assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(1)
        assertThat(f.contar("idempotencia")).isEqualTo(1)
        assertThat(jdbc.queryForObject("SELECT versao FROM recebiveis WHERE id = ?", Long::class.java, original.recebivelId))
            .isEqualTo(1L)
        val simultaneo = f.comando()
        val poolReplay = Executors.newFixedThreadPool(2)
        try {
            val tarefas = (1..2).map { poolReplay.submit<Any> { f.servico.liquidar(simultaneo) } }
            assertThat(tarefas[0].get(20, TimeUnit.SECONDS)).isEqualTo(tarefas[1].get(20, TimeUnit.SECONDS))
        } finally { poolReplay.shutdownNow() }
        assertThat(f.contar("liquidacoes")).isEqualTo(2)
    }

    @Test
    fun `modo ou validade alterados entre preparacao e transacao causam rollback integral`() {
        val f = Fixture()
        f.ptax()
        val comando = f.comando()
        val snapshot = f.taxas.consultar(modalidade, true)
        val manual = f.historico.registrarManual(f.autor, BigDecimal("6"), "Contingência")
        f.agora = f.agora.plusSeconds(1)
        f.historico.selecionar(f.autor, FonteCambial.MANUAL, manual.id, "Autorizar")
        val recebivel = RecebiveisJdbc(jdbc).buscar(f.org, comando.recebivelId)!!
        val preco = br.com.srm.creditengine.precificacao.aplicacao.precificarComTaxas(
            modalidade, recebivel.valorFaceBrl, recebivel.prazoMeses, snapshot)
        val liquidacao = br.com.srm.creditengine.liquidacao.dominio.Liquidacao(UUID.randomUUID(), recebivel,
            snapshot, preco, MoedaPagamento.USD, checkNotNull(preco.valorPresenteUsd))
        assertThrows<ConflitoLiquidacao> {
            f.liquidacao.confirmar(IdentidadeLiquidacao(f.org, f.usuario), "obsoleta", ByteArray(32), liquidacao)
        }
        assertThat(f.contar("liquidacoes")).isZero()
        assertThat(f.contar("auditoria_liquidacoes")).isZero()
        assertThat(f.contar("idempotencia")).isZero()
    }

    @Test
    fun `mudanca de taxa base ou spread apos previa exige nova confirmacao mesmo com cambio identico`() {
        val f = Fixture()
        f.ptax()
        val comando = f.comando()
        f.agora = f.agora.plusSeconds(1)
        f.taxas.registrarTaxaBase(BigDecimal("0.02"), f.agora)
        assertThrows<ConflitoLiquidacao> { f.servico.liquidar(comando) }
        val novaPrevia = f.comando(comando.recebivelId)
        f.agora = f.agora.plusSeconds(1)
        f.taxas.registrarSpread(modalidade, BigDecimal("0.025"), f.agora)
        assertThrows<ConflitoLiquidacao> { f.servico.liquidar(novaPrevia) }
        assertThat(f.contar("liquidacoes")).isZero()
        assertThat(f.contar("idempotencia")).isZero()
        assertThat(f.servico.liquidar(f.comando(comando.recebivelId)).liquidacao.valorPagamento)
            .isNotEqualByComparingTo("15768.64")
    }

    @Test
    fun `validade DEMO configurada de uma hora e preservada na revalidacao transacional`() {
        val f = Fixture()
        val historico = HistoricoCambioJdbc(jdbc, f.transacao, f.clock, modoDemo = true, validadeDemo = Duration.ofHours(1))
        f.taxas.registrarCotacao(BigDecimal("5.4321"), f.referencia.minus(Duration.ofMinutes(45)))
        val taxas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofHours(1)), f.clock,
            ConsultarCambio { historico.consultar(f.org, it) })
        val persistencia = LiquidacaoJdbc(jdbc, f.transacao, f.clock, historicoCambio = historico)
        val servico = ServicoLiquidacao(IdentidadeConfiavel { IdentidadeLiquidacao(f.org, f.usuario) },
            RecebiveisJdbc(jdbc), persistencia, taxas, persistencia, f.clock)
        val resultado = servico.liquidar(ComandoLiquidacao(f.recebivel(), MoedaPagamento.USD, "demo-60"))
        assertThat(resultado.liquidacao.valorPagamento).isEqualByComparingTo("17094.67")
    }

    @Test
    fun `coordenacao PostgreSQL entre instancias impede resposta antiga chegar depois de revisao nova`() {
        val f = Fixture()
        val entrada = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        val coordenacaoA = CoordenacaoPtaxJdbc(checkNotNull(jdbc.dataSource))
        val coordenacaoB = CoordenacaoPtaxJdbc(checkNotNull(jdbc.dataSource))
        try {
            val antiga = pool.submit<Int> {
                coordenacaoA.executar {
                    assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
                    entrada.countDown()
                    check(liberar.await(10, TimeUnit.SECONDS))
                    f.ptax()
                }
            }
            check(entrada.await(10, TimeUnit.SECONDS))
            var httpNovaIniciado = false
            assertThrows<ConsultaCambialIndisponivel> {
                coordenacaoB.executar { httpNovaIniciado = true; f.ptax("6.1000") }
            }
            assertThat(httpNovaIniciado).isFalse()
            liberar.countDown()
            assertThat(antiga.get(15, TimeUnit.SECONDS)).isEqualTo(1)
            assertThat(coordenacaoB.executar { f.ptax("6.1000") }).isEqualTo(1)
            assertThat(f.historico.consultar(f.org, f.agora).cotacao.taxaBrlPorUsd).isEqualByComparingTo("6.1000")
        } finally { liberar.countDown(); pool.shutdownNow() }
    }

    @Test
    fun `migration V7 preserva valor e nao atribui PTAX a historico sem proveniencia`() {
        val schema = "b15_migration_" + UUID.randomUUID().toString().replace("-", "")
        val flyway = org.flywaydb.core.Flyway.configure().dataSource(checkNotNull(jdbc.dataSource))
            .schemas(schema).defaultSchema(schema).target("6").load()
        try {
            flyway.migrate()
            val id = UUID.randomUUID()
            jdbc.update("""INSERT INTO $schema.cotacoes_cambiais (id, taxa_brl_por_unidade, vigente_em)
                VALUES (?, 5.4321, '2026-11-19T16:00:00Z')""", id)
            org.flywaydb.core.Flyway.configure().dataSource(checkNotNull(jdbc.dataSource))
                .schemas(schema).defaultSchema(schema).load().migrate()
            val legado = jdbc.queryForMap("SELECT fonte, taxa_brl_por_unidade FROM $schema.cotacoes_cambiais WHERE id = ?", id)
            assertThat(legado["fonte"]).isEqualTo("LEGADO")
            assertThat(legado["taxa_brl_por_unidade"] as BigDecimal).isEqualByComparingTo("5.4321")
        } finally {
            jdbc.execute("DROP SCHEMA $schema CASCADE")
        }
    }

    companion object {
        private val sequencia = AtomicInteger()
        private val FUSO = ZoneId.of("America/Sao_Paulo")
    }
}
