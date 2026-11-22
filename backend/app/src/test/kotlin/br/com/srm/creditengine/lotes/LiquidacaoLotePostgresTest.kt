package br.com.srm.creditengine.lotes

import br.com.srm.creditengine.cadastro.adaptador.saida.CedentesJdbc
import br.com.srm.creditengine.cadastro.adaptador.saida.RecebiveisJdbc
import br.com.srm.creditengine.extrato.adaptador.saida.ExtratoJdbc
import br.com.srm.creditengine.extrato.aplicacao.FiltroExtrato
import br.com.srm.creditengine.liquidacao.adaptador.saida.LiquidacaoJdbc
import br.com.srm.creditengine.liquidacao.aplicacao.ServicoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ComandoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.Liquidacao
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.liquidacao.dominio.RecebivelNaoEncontrado
import br.com.srm.creditengine.lotes.adaptador.entrada.LeitorArquivoLote
import br.com.srm.creditengine.lotes.adaptador.saida.LiquidacaoLoteJdbc
import br.com.srm.creditengine.lotes.adaptador.saida.LotesJdbc
import br.com.srm.creditengine.lotes.aplicacao.ItemImportacao
import br.com.srm.creditengine.lotes.aplicacao.ItemLiquidacaoLote
import br.com.srm.creditengine.lotes.aplicacao.ResultadoLiquidacaoLote
import br.com.srm.creditengine.lotes.aplicacao.ServicoLiquidacaoLote
import br.com.srm.creditengine.lotes.aplicacao.StatusLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.porta.saida.PersistenciaLiquidacaoLote
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoCambioJdbc
import br.com.srm.creditengine.taxas.aplicacao.BoletimPtax
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.ConsultarCambio
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.dao.QueryTimeoutException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LiquidacaoLotePostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var gerenciador: PlatformTransactionManager
    @Autowired private lateinit var json: ObjectMapper

    private fun fixture(quantidade: Int = 200, ptax: Boolean = false): Fixture {
        val org = UUID.randomUUID()
        val usuario = UUID.randomUUID()
        val cedente = UUID.randomUUID()
        val clock = Relogio(Instant.parse("2180-01-01T00:00:00Z").plusSeconds(sequencia.incrementAndGet() * 86400L))
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'B17 teste')", org)
        jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash) VALUES (?, ?, ?, 'hash')",
            usuario, org, "$usuario@example.invalid")
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'B17 teste', 'teste')",
            cedente, org)
        val cambio = HistoricoCambioJdbc(jdbc, TransactionTemplate(gerenciador), clock)
        val taxas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), clock,
            if (ptax) ConsultarCambio { cambio.consultar(org, it) } else null)
        taxas.registrarTaxaBase(BigDecimal("0.01"), clock.instant())
        taxas.registrarSpread("DUPLICATA_MERCANTIL", BigDecimal("0.015"), clock.instant())
        taxas.registrarSpread("CHEQUE_PRE_DATADO", BigDecimal("0.025"), clock.instant())
        if (ptax) {
            val local = clock.instant().atZone(ZoneId.of("America/Sao_Paulo"))
            cambio.sincronizar(listOf(BoletimPtax(BigDecimal("6.0994"), BigDecimal("6.1000"),
                clock.instant(), local.toLocalDate(), local.toLocalDateTime().toString().replace('T', ' '))))
        } else taxas.registrarCotacao(BigDecimal("5.4321"), clock.instant())
        val itens = (1..quantidade).map {
            ItemImportacao("b17-$it", cedente,
                if (it == 2) "CHEQUE_PRE_DATADO" else "DUPLICATA_MERCANTIL",
                BigDecimal(if (it == 2) "25000.00" else "100000.00"), if (it == 2) 2 else 3,
                null, if (ptax || it == 3) MoedaPagamento.USD else MoedaPagamento.BRL)
        }
        val lote = LotesJdbc(jdbc, TransactionTemplate(gerenciador)).confirmar(
            IdentidadeUsuario(usuario, org), "cadastro", "a".repeat(64), itens,
        ) {}
        return Fixture(org, usuario, cedente, clock, taxas, lote.loteId,
            lote.itens.mapIndexed { i, item -> ItemLiquidacaoLote(item.recebivelId, itens[i].moedaPagamento) })
    }

    private inner class Fixture(
        val org: UUID, val usuario: UUID, val cedente: UUID, val clock: Relogio,
        val taxas: ManterEConsultarTaxas, val lote: UUID, val itens: List<ItemLiquidacaoLote>,
    ) {
        val identidade = IdentidadeLiquidacao(org, usuario)
        val confirmacoes = AtomicInteger()
        val replays = AtomicInteger()
        fun repositorio(template: JdbcTemplate = jdbc) =
            LiquidacaoLoteJdbc(template, TransactionTemplate(gerenciador), clock, json,
                aposCommit = { confirmacoes.incrementAndGet() }, aposReplay = { replays.incrementAndGet() })
        fun servico(porta: PersistenciaLiquidacaoLote = repositorio(), id: IdentidadeLiquidacao = identidade) =
            ServicoLiquidacaoLote(IdentidadeConfiavel { id }, CedentesJdbc(jdbc), porta, taxas, clock, 200)
        fun hash() = checkNotNull(servico().previa(lote, itens).hashPrevia)
        fun confirmar(chave: String, hash: String, porta: PersistenciaLiquidacaoLote = repositorio()) =
            servico(porta).confirmar(lote, itens, hash, true, chave)
        fun contar(tabela: String): Int =
            jdbc.queryForObject("SELECT count(*) FROM $tabela WHERE organizacao_id = ?", Int::class.java, org)!!
        fun verificar(confirmados: Int, chaves: Int) {
            assertThat(contar("liquidacoes")).isEqualTo(confirmados)
            assertThat(contar("auditoria_liquidacoes")).isEqualTo(confirmados)
            assertThat(contar("idempotencia")).isEqualTo(chaves)
            assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM recebiveis WHERE organizacao_id = ? AND status = 'LIQUIDADO' AND versao = 1",
                Int::class.java, org)).isEqualTo(confirmados)
            assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM recebiveis WHERE organizacao_id = ? AND status = 'DISPONIVEL' AND versao = 0",
                Int::class.java, org)).isEqualTo(itens.size - confirmados)
            assertThat(jdbc.queryForObject(
                """SELECT count(*) FROM liquidacoes l JOIN recebiveis r
                   ON r.id = l.recebivel_id AND r.organizacao_id = l.organizacao_id
                   WHERE l.organizacao_id = ? AND l.versao_recebivel = r.versao""",
                Int::class.java, org)).isEqualTo(confirmados)
            val extrato = ExtratoJdbc(jdbc).consultar(org, FiltroExtrato(null, null, null, null, 0, 100))
            assertThat(extrato.total).isEqualTo(confirmados.toLong())
            val paginas = (0 until ((confirmados + 99) / 100)).flatMap {
                ExtratoJdbc(jdbc).consultar(org, FiltroExtrato(null, null, null, null, it, 100)).itens
            }
            assertThat(paginas.map { it.liquidacaoId }.distinct()).hasSize(confirmados)
            for (item in paginas) {
                val snapshot = jdbc.queryForMap("SELECT * FROM liquidacoes WHERE id = ?", item.liquidacaoId)
                assertThat(item.recebivelId).isEqualTo(snapshot["recebivel_id"])
                assertThat(item.valorPresenteBrl).isEqualByComparingTo(snapshot["valor_presente_brl"] as BigDecimal)
                assertThat(item.desagioBrl).isEqualByComparingTo(snapshot["desagio_brl"] as BigDecimal)
                assertThat(item.valorPagamento).isEqualByComparingTo(snapshot["valor_pagamento"] as BigDecimal)
                assertThat(jdbc.queryForObject(
                    """SELECT count(*) FROM auditoria_liquidacoes WHERE liquidacao_id = ? AND organizacao_id = ?
                       AND usuario_id = ? AND evento = 'LIQUIDACAO_CONFIRMADA'""",
                    Int::class.java, item.liquidacaoId, org, usuario)).isEqualTo(1)
            }
            assertThat(jdbc.queryForObject(
                """SELECT count(*) FROM (SELECT recebivel_id FROM liquidacoes WHERE organizacao_id = ?
                   GROUP BY recebivel_id HAVING count(*) > 1) duplicadas""", Int::class.java, org)).isZero()
        }
    }

    @Test
    fun `PTAX em 200 itens concorrentes confirma atomicamente uma vez e replay sobrevive a 97h`() {
        val f = fixture(ptax = true)
        val hash = f.hash()
        val barreira = CyclicBarrier(2)
        val porta = sincronizar(f.repositorio()) { barreira.await(20, TimeUnit.SECONDS) }
        val resultados = paralelo({ f.confirmar("ptax-a", hash, porta) }, { f.confirmar("ptax-b", hash, porta) })
        assertThat(resultados.count { it.isSuccess }).isEqualTo(1)
        assertThat(resultados.single { it.isFailure }.exceptionOrNull()).isInstanceOf(ConflitoLiquidacao::class.java)
        val vencedor = resultados.single { it.isSuccess }.getOrThrow()
        f.verificar(200, 1)
        assertThat(jdbc.queryForObject(
            """SELECT count(*) FROM liquidacoes WHERE organizacao_id = ?
               AND cambio_snapshot->>'fonte' = 'BCB_PTAX' AND politica_cambial_id IS NOT NULL""",
            Int::class.java, f.org)).isEqualTo(200)
        f.clock.instante.set(f.clock.instant().plus(Duration.ofHours(97)))
        val chave = if (resultados[0].isSuccess) "ptax-a" else "ptax-b"
        assertThat(f.confirmar(chave, hash)).isEqualTo(vencedor)
        f.verificar(200, 1)
    }

    @Test
    fun `duas transacoes do mesmo lote de 200 com chaves distintas confirmam exatamente 200 uma unica vez`() {
        val f = fixture()
        val hash = f.hash()
        val barreira = CyclicBarrier(2)
        val pids = ConcurrentHashMap.newKeySet<Int>()
        val porta = sincronizar(f.repositorio()) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue()
            pids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Int::class.java)!!)
            barreira.await(20, TimeUnit.SECONDS)
        }
        val resultados = paralelo({ f.confirmar("a", hash, porta) }, { f.confirmar("b", hash, porta) })
        assertThat(pids).hasSize(2)
        assertThat(resultados.count { it.isSuccess }).isEqualTo(1)
        assertThat(resultados.single { it.isFailure }.exceptionOrNull()).isInstanceOf(ConflitoLiquidacao::class.java)
        val vencedor = resultados.single { it.isSuccess }.getOrThrow()
        assertThat(vencedor.itens).hasSize(200)
        for (item in vencedor.itens) {
            val snapshot = jdbc.queryForMap("SELECT * FROM liquidacoes WHERE id = ?", item.liquidacaoId)
            assertThat(snapshot["recebivel_id"]).isEqualTo(item.recebivelId)
            assertThat(snapshot["moeda_pagamento"]).isEqualTo(item.moedaPagamento)
            assertThat(snapshot["valor_presente_brl"] as BigDecimal).isEqualByComparingTo(item.valorPresenteBrl)
            assertThat(snapshot["desagio_brl"] as BigDecimal).isEqualByComparingTo(item.desagioBrl)
            assertThat(snapshot["valor_pagamento"] as BigDecimal).isEqualByComparingTo(item.valorPagamento)
        }
        f.verificar(200, 1)
        assertThat(f.confirmacoes.get()).isEqualTo(1)
        assertThat(jdbc.queryForObject(
            """SELECT count(*) FROM idempotencia WHERE organizacao_id = ? AND tipo_operacao = 'LIQUIDAR_LOTE'
               AND situacao = 'CONCLUIDA' AND jsonb_array_length(resposta_corpo->'itens') = 200""",
            Int::class.java, f.org)).isEqualTo(1)
    }

    @Test
    fun `mesma chave concorrente executa financeiramente uma vez e retorna replay exato apos resposta perdida`() {
        val f = fixture()
        val hash = f.hash()
        val barreira = CyclicBarrier(2)
        val pids = ConcurrentHashMap.newKeySet<Int>()
        val preparacoes = AtomicInteger()
        val template = ObservadorJdbc(antes = { sql ->
            if (sql.contains("INSERT INTO idempotencia")) {
                pids.add(jdbc.queryForObject("SELECT pg_backend_pid()", Int::class.java)!!)
                barreira.await(20, TimeUnit.SECONDS)
            }
        })
        val porta = sincronizar(f.repositorio(template)) { preparacoes.incrementAndGet() }
        val resultados = paralelo({ f.confirmar("mesma", hash, porta) }, { f.confirmar("mesma", hash, porta) })
        assertThat(pids).hasSize(2)
        resultados.forEach { assertThat(it.isSuccess).isTrue() }
        assertThat(resultados[0].getOrThrow()).isEqualTo(resultados[1].getOrThrow())
        assertThat(preparacoes.get()).isEqualTo(1)
        assertThat(f.confirmacoes.get()).isEqualTo(1)
        assertThat(f.replays.get()).isEqualTo(1)
        f.clock.instante.set(f.clock.instant().plusSeconds(3600))
        assertThat(f.confirmar("mesma", hash)).isEqualTo(resultados[0].getOrThrow())
        assertThrows<ConflitoLiquidacao> { f.confirmar("mesma", "f".repeat(64)) }
        f.verificar(200, 1)
    }

    @Test
    fun `conflito concorrente tardio reverte cinco snapshots e auditorias provisorias do lote rejeitado`() {
        val f = fixture()
        val hash = f.hash()
        val gravou = CountDownLatch(1)
        val continuar = CountDownLatch(1)
        val escritas = AtomicInteger()
        val template = ObservadorJdbc(depois = { sql ->
            if (sql.contains("INSERT INTO auditoria_liquidacoes") && escritas.incrementAndGet() == 5) {
                assertThat(f.contar("liquidacoes")).isEqualTo(5)
                assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(5)
                gravou.countDown()
                check(continuar.await(20, TimeUnit.SECONDS))
            }
        })
        val executor = Executors.newSingleThreadExecutor()
        try {
            val tentativa = executor.submit<Result<ResultadoLiquidacaoLote>> {
                runCatching { f.confirmar("perdedor", hash, f.repositorio(template)) }
            }
            check(gravou.await(20, TimeUnit.SECONDS))
            f.verificar(0, 0)
            val alvo = f.itens.sortedBy { it.recebivelId.toString() }[150]
            val financeiro = LiquidacaoJdbc(jdbc, TransactionTemplate(gerenciador), f.clock)
            val vencedor = ServicoLiquidacao(IdentidadeConfiavel { f.identidade }, RecebiveisJdbc(jdbc),
                financeiro, f.taxas, financeiro, f.clock)
                .liquidar(ComandoLiquidacao(alvo.recebivelId, alvo.moedaPagamento, "vencedor-individual"))
            continuar.countDown()
            assertThat(tentativa.get(30, TimeUnit.SECONDS).exceptionOrNull()).isInstanceOf(ConflitoLiquidacao::class.java)
            f.verificar(1, 1)
            assertThat(escritas.get()).isEqualTo(150)
            assertThat(jdbc.queryForObject("SELECT id FROM liquidacoes WHERE organizacao_id = ?",
                UUID::class.java, f.org)).isEqualTo(vencedor.liquidacao.liquidacaoId)
            assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM idempotencia WHERE organizacao_id = ? AND chave = 'perdedor'",
                Int::class.java, f.org)).isZero()
            assertThat(f.confirmacoes.get()).isZero()
        } finally {
            continuar.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `duas liquidacoes distintas do mesmo recebivel tem uma confirmacao e um conflito otimista`() {
        val f = fixture(1)
        val barreira = CyclicBarrier(2)
        val porta = sincronizar(f.repositorio()) { barreira.await(20, TimeUnit.SECONDS) }
        val hash = f.hash()
        val resultados = paralelo({ f.confirmar("um", hash, porta) }, { f.confirmar("dois", hash, porta) })
        assertThat(resultados.count { it.isSuccess }).isEqualTo(1)
        assertThat(resultados.single { it.isFailure }.exceptionOrNull()).isInstanceOf(ConflitoLiquidacao::class.java)
        f.verificar(1, 1)
    }

    @Test
    fun `falhas apos a metade e penultimo titulo fazem rollback de todas as tabelas e versoes`() {
        for (falharEm in listOf(100, 199)) {
            val f = fixture()
            val escritas = AtomicInteger()
            val template = ObservadorJdbc(depois = { sql ->
                if (sql.contains("INSERT INTO auditoria_liquidacoes") && escritas.incrementAndGet() == falharEm) {
                    assertThat(f.contar("liquidacoes")).isEqualTo(falharEm)
                    assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(falharEm)
                    throw FalhaInduzida()
                }
            })
            assertThrows<FalhaInduzida> { f.confirmar("falha", f.hash(), f.repositorio(template)) }
            assertThat(escritas.get()).isEqualTo(falharEm)
            f.verificar(0, 0)
            assertThat(f.confirmacoes.get()).isZero()
        }
    }

    @Test
    fun `timeout PostgreSQL apos escritas provisorias reverte lote e mesma chave permite recuperacao segura`() {
        val f = fixture(10)
        val hash = f.hash()
        val escritas = AtomicInteger()
        val template = ObservadorJdbc(depois = { sql ->
            if (sql.contains("INSERT INTO auditoria_liquidacoes") && escritas.incrementAndGet() == 5) {
                assertThat(f.contar("liquidacoes")).isEqualTo(5)
                jdbc.execute("SET LOCAL statement_timeout = '50ms'")
                jdbc.execute("SELECT pg_sleep(1)")
            }
        })
        assertThrows<QueryTimeoutException> { f.confirmar("timeout", hash, f.repositorio(template)) }
        assertThat(escritas.get()).isEqualTo(5)
        f.verificar(0, 0)
        val confirmado = f.confirmar("timeout", hash)
        assertThat(f.confirmar("timeout", hash)).isEqualTo(confirmado)
        f.verificar(10, 1)
    }

    @Test
    fun `previa integral sem escrita preserva golden cases exige confirmacao e permite apenas lote completo`() {
        val f = fixture()
        val previa = f.servico().previa(f.lote, f.itens)
        assertThat(previa.aptoParaLiquidar).isTrue()
        assertThat(previa.itens).hasSize(200)
        assertThat(previa.itens.single { it.recebivelId == f.itens[0].recebivelId }.valorPresenteBrl).isEqualTo("92859.94")
        assertThat(previa.itens.single { it.recebivelId == f.itens[1].recebivelId }.valorPagamento).isEqualTo("23337.77")
        assertThat(previa.itens.single { it.recebivelId == f.itens[2].recebivelId }.valorPagamento).isEqualTo("17094.67")
        assertThat(previa.totaisPorMoeda["USD"]).isEqualTo("17094.67")
        val hash = checkNotNull(previa.hashPrevia)
        assertThrows<IllegalArgumentException> { f.servico().confirmar(f.lote, f.itens, hash, false, "nao") }
        assertThrows<IllegalArgumentException> { f.servico().confirmar(f.lote, f.itens.dropLast(1), hash, true, "parcial") }
        assertThrows<IllegalArgumentException> { f.servico().previa(f.lote, f.itens + f.itens.first()) }
        f.verificar(0, 0)
        f.confirmar("golden", hash)
        for ((indice, vp, pagamento, desagio) in listOf(
            listOf("0", "92859.94", "92859.94", "7140.06"),
            listOf("1", "23337.77", "23337.77", "1662.23"),
            listOf("2", "92859.94", "17094.67", "7140.06"),
        )) {
            val snapshot = jdbc.queryForMap("SELECT * FROM liquidacoes WHERE recebivel_id = ?", f.itens[indice.toInt()].recebivelId)
            assertThat(snapshot["valor_presente_brl"] as BigDecimal).isEqualByComparingTo(vp)
            assertThat(snapshot["valor_pagamento"] as BigDecimal).isEqualByComparingTo(pagamento)
            assertThat(snapshot["desagio_brl"] as BigDecimal).isEqualByComparingTo(desagio)
        }
        val liquidada = f.servico().previa(f.lote, f.itens)
        assertThat(liquidada.aptoParaLiquidar).isFalse()
        assertThat(liquidada.hashPrevia).isNull()
        assertThat(liquidada.itens.map { it.impedimento }.toSet()).containsExactly("JA_LIQUIDADO")
    }

    @Test
    fun `mudancas de versao taxa base spread cambio e cotacao expirada invalidam previa sem efeitos`() {
        for (mudanca in listOf("versao", "base", "spread", "cambio", "expirada", "cedente")) {
            val f = fixture(3)
            val hash = f.hash()
            f.clock.instante.set(f.clock.instant().plusSeconds(1))
            when (mudanca) {
                "versao" -> jdbc.update("UPDATE recebiveis SET versao = versao + 1 WHERE id = ?", f.itens[0].recebivelId)
                "base" -> f.taxas.registrarTaxaBase(BigDecimal("0.02"), f.clock.instant())
                "spread" -> f.taxas.registrarSpread("DUPLICATA_MERCANTIL", BigDecimal("0.03"), f.clock.instant())
                "cambio" -> f.taxas.registrarCotacao(BigDecimal("6.0000"), f.clock.instant())
                "expirada" -> f.clock.instante.set(f.clock.instant().plusSeconds(1800))
                "cedente" -> jdbc.update("UPDATE cedentes SET ativo = false WHERE id = ?", f.cedente)
            }
            if (mudanca == "expirada") {
                assertThrows<DadosFinanceirosIndisponiveis> { f.confirmar(mudanca, hash) }
            } else assertThrows<ConflitoLiquidacao> { f.confirmar(mudanca, hash) }
            assertThat(f.contar("liquidacoes")).isZero()
            assertThat(f.contar("auditoria_liquidacoes")).isZero()
            assertThat(f.contar("idempotencia")).isZero()
            assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM recebiveis WHERE organizacao_id = ? AND status = 'LIQUIDADO'",
                Int::class.java, f.org)).isZero()
        }
    }

    @Test
    fun `vencimento cambial durante escritas reverte todo lote e previa incompleta nao gera autorizacao`() {
        val f = fixture(3)
        val hash = f.hash()
        val template = ObservadorJdbc(depois = { sql ->
            if (sql.contains("INSERT INTO auditoria_liquidacoes")) {
                f.clock.instante.set(f.clock.instant().plusSeconds(1801))
            }
        })
        assertThrows<DadosFinanceirosIndisponiveis> { f.confirmar("vencendo", hash, f.repositorio(template)) }
        f.verificar(0, 0)
        val previa = f.servico().previa(f.lote, f.itens)
        assertThat(previa.aptoParaLiquidar).isFalse()
        assertThat(previa.hashPrevia).isNull()
        assertThat(previa.itens).hasSize(3)
        assertThat(previa.itens.count { it.impedimento == "DADOS_FINANCEIROS_INDISPONIVEIS" }).isEqualTo(1)
    }

    @Test
    fun `previa bloqueia valor arredondado a zero e limite configurado menor que o lote`() {
        val f = fixture(3)
        jdbc.update("UPDATE recebiveis SET valor_face_brl = 0.01, prazo_meses = 100 WHERE id = ?", f.itens[0].recebivelId)
        val previa = f.servico().previa(f.lote, f.itens)
        assertThat(previa.aptoParaLiquidar).isFalse()
        assertThat(previa.hashPrevia).isNull()
        assertThat(previa.itens.single { it.recebivelId == f.itens[0].recebivelId }.impedimento)
            .isEqualTo("PRECIFICACAO_INVALIDA")
        val limitado = ServicoLiquidacaoLote(IdentidadeConfiavel { f.identidade }, CedentesJdbc(jdbc),
            f.repositorio(), f.taxas, f.clock, 2)
        assertThrows<IllegalArgumentException> { limitado.previa(f.lote, f.itens) }
        f.verificar(0, 0)
    }

    @Test
    fun `lote alheio e itens de outra organizacao sao rejeitados e chave nao cruza organizacoes`() {
        val f = fixture(3)
        val g = fixture(3)
        val hash = f.hash()
        assertThrows<RecebivelNaoEncontrado> { g.servico().previa(f.lote, f.itens) }
        assertThrows<RecebivelNaoEncontrado> { g.servico().confirmar(f.lote, f.itens, hash, true, "alheio") }
        assertThrows<IllegalArgumentException> { f.servico().previa(f.lote, g.itens) }
        f.verificar(0, 0)
        g.verificar(0, 0)
        f.confirmar("mesma-chave", f.hash())
        g.confirmar("mesma-chave", g.hash())
        f.verificar(3, 1)
        g.verificar(3, 1)
    }

    @Test
    fun `massa A liquidada faz quatro referencias do arquivo B aparecerem JA_LIQUIDADO`() {
        val f = fixture(1)
        val linhasA = lerMassa("SRM_LOTE_A_200_RECEBIVEIS.csv", f.cedente)
        val cadastro = LotesJdbc(jdbc, TransactionTemplate(gerenciador))
        val loteA = cadastro.confirmar(IdentidadeUsuario(f.usuario, f.org), "massa", "b".repeat(64),
            linhasA.map { checkNotNull(it.item) }) {}
        val itensA = loteA.itens.mapIndexed { i, item ->
            ItemLiquidacaoLote(item.recebivelId, checkNotNull(linhasA[i].item).moedaPagamento)
        }
        val previa = f.servico().previa(loteA.loteId, itensA)
        f.servico().confirmar(loteA.loteId, itensA, checkNotNull(previa.hashPrevia), true, "liquidar-A")
        val linhasB = lerMassa("SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv", f.cedente)
        val encontrados = cadastro.consultarReferencias(f.org, linhasB.map { checkNotNull(it.item).referenciaExterna }.toSet())
        assertThat(encontrados).hasSize(4)
        assertThat(encontrados.values.map { it.status }.toSet()).containsExactly(StatusLinhaLote.JA_LIQUIDADO)
        assertThat(f.contar("liquidacoes")).isEqualTo(200)
        assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(200)
    }

    private fun lerMassa(nome: String, cedente: UUID): List<br.com.srm.creditengine.lotes.aplicacao.LinhaImportacao> {
        val arquivo = sequenceOf(Path.of("../.."), Path.of(".."), Path.of("."))
            .map { it.resolve("docs/validacao/lotes").resolve(nome) }.first { Files.isRegularFile(it) }
        val bytes = Files.readAllLines(arquivo).mapIndexed { i, linha ->
            if (i == 0) linha else linha.split(';').toMutableList().apply { this[1] = cedente.toString() }.joinToString(";")
        }.joinToString("\n").toByteArray()
        return LeitorArquivoLote(200, 2097152).ler(nome, bytes.size.toLong(), bytes.inputStream())
    }

    private fun sincronizar(
        porta: PersistenciaLiquidacaoLote, aposPreparar: () -> Unit,
    ): PersistenciaLiquidacaoLote = object : PersistenciaLiquidacaoLote by porta {
        override fun confirmar(
            identidade: IdentidadeLiquidacao, loteId: UUID, chave: String, hash: ByteArray,
            preparar: () -> List<Liquidacao>, revalidar: (List<Liquidacao>) -> Unit,
        ) = porta.confirmar(identidade, loteId, chave, hash, {
            preparar().also { aposPreparar() }
        }, revalidar)
    }

    private inner class ObservadorJdbc(
        val antes: (String) -> Unit = {}, val depois: (String) -> Unit = {},
    ) : JdbcTemplate(checkNotNull(jdbc.dataSource)) {
        override fun update(sql: String, vararg args: Any?): Int {
            antes(sql)
            return super.update(sql, *args).also { depois(sql) }
        }
    }

    private fun <T> paralelo(a: () -> T, b: () -> T): List<Result<T>> {
        val executor = Executors.newFixedThreadPool(2)
        return try {
            val futures = listOf(a, b).map { tarefa -> executor.submit<Result<T>> { runCatching(tarefa) } }
            futures.map { it.get(45, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private class FalhaInduzida : RuntimeException()
    private class Relogio(inicial: Instant) : Clock() {
        val instante = AtomicReference(inicial)
        override fun instant(): Instant = instante.get()
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
    }
    companion object { private val sequencia = AtomicInteger() }
}
