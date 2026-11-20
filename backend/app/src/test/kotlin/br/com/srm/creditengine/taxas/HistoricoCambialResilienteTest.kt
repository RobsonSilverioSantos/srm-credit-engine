package br.com.srm.creditengine.taxas

import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.taxas.adaptador.saida.ConsultaCambialIndisponivel
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoCambialResiliente
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoFinanceiroPorta
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.dominio.HistoricoElegivel
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import br.com.srm.creditengine.taxas.dominio.SpreadRecebivel
import br.com.srm.creditengine.taxas.dominio.TaxaBase
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
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
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager

class HistoricoCambialResilienteTest {
    private val referencia = Instant.parse("2026-11-20T20:00:00Z")
    private val modalidade = Modalidades.DUPLICATA_MERCANTIL
    private val base = TaxaBase(UUID.randomUUID(), BigDecimal("0.01"), referencia, referencia)
    private val spread = SpreadRecebivel(UUID.randomUUID(), modalidade, BigDecimal("0.015"), referencia, referencia)
    private val cotacao = CotacaoCambial(UUID.randomUUID(), BigDecimal("5.4321"), referencia, referencia)
    private val dados = HistoricoElegivel(base, spread, cotacao)

    private class HistoricoFake(
        val consulta: (String, Instant, Boolean) -> HistoricoElegivel,
    ) : HistoricoFinanceiroPorta {
        val escritas = AtomicInteger()
        override fun incluir(taxa: TaxaBase) { escritas.incrementAndGet() }
        override fun incluir(spread: SpreadRecebivel) { escritas.incrementAndGet() }
        override fun incluir(cotacao: CotacaoCambial) { escritas.incrementAndGet() }
        override fun consultar(modalidade: String, referencia: Instant, incluirCotacao: Boolean) =
            consulta(modalidade, referencia, incluirCotacao)
    }

    private fun resiliente(
        historico: HistoricoFinanceiroPorta,
        timeout: Duration = Duration.ofSeconds(1),
        tentativas: Int = 3,
        atraso: Duration = Duration.ZERO,
    ) = HistoricoCambialResiliente(historico, timeout, tentativas, atraso)

    @Test
    fun `consulta com sucesso e preserva snapshot`() {
        val chamadas = AtomicInteger()
        val historico = HistoricoFake { _, instante, incluir ->
            chamadas.incrementAndGet()
            assertThat(instante).isEqualTo(referencia)
            assertThat(incluir).isTrue()
            dados
        }
        resiliente(historico).use {
            val resultado = ManterEConsultarTaxas(it, SeletorTaxas(Duration.ofMinutes(30)), Clock.fixed(referencia, ZoneOffset.UTC))
                .consultar(modalidade, true)
            assertThat(resultado.cotacao).isEqualTo(cotacao)
            assertThat(resultado.cotacaoValidaAte).isEqualTo(referencia.plusSeconds(1800))
            assertThat(resultado.referencia).isEqualTo(referencia)
        }
        assertThat(chamadas.get()).isEqualTo(1)
    }

    @Test
    fun `repete falhas transitorias com o mesmo instante UTC e recupera`() {
        val chamadas = AtomicInteger()
        var leituras = 0
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.ofHours(-3)
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = referencia.plusSeconds(leituras++.toLong())
        }
        val historico = HistoricoFake { _, instante, _ ->
            assertThat(instante).isEqualTo(referencia)
            if (chamadas.incrementAndGet() < 3) throw QueryTimeoutException("temporário")
            dados
        }
        resiliente(historico, atraso = Duration.ofMillis(1)).use {
            val resultado = ManterEConsultarTaxas(it, SeletorTaxas(Duration.ofMinutes(30)), clock).consultar(modalidade, true)
            assertThat(resultado.referencia).isEqualTo(referencia)
            assertThat(resultado.cotacao).isEqualTo(cotacao)
        }
        assertThat(chamadas.get()).isEqualTo(3)
        assertThat(leituras).isEqualTo(1)
    }

    @Test
    fun `esgota tentativas somente para falha transitoria`() {
        val chamadas = AtomicInteger()
        val historico = HistoricoFake { _, _, _ ->
            chamadas.incrementAndGet()
            throw QueryTimeoutException("temporário")
        }
        resiliente(historico, tentativas = 2).use {
            val erro = assertThrows<ConsultaCambialIndisponivel> { it.consultar(modalidade, referencia, true) }
            assertThat(erro.cause).isInstanceOf(QueryTimeoutException::class.java)
        }
        assertThat(chamadas.get()).isEqualTo(2)
    }

    @Test
    fun `timeout cancela leitura e resulta em erro explicito`() {
        val chamadas = AtomicInteger()
        val terminou = CountDownLatch(1)
        val historico = HistoricoFake { _, _, _ ->
            chamadas.incrementAndGet()
            try {
                CountDownLatch(1).await()
                dados
            } finally {
                terminou.countDown()
            }
        }
        resiliente(historico, timeout = Duration.ofMillis(100), tentativas = 1).use {
            val erro = assertThrows<ConsultaCambialIndisponivel> { it.consultar(modalidade, referencia, true) }
            assertThat(erro.cause).isInstanceOf(java.util.concurrent.TimeoutException::class.java)
            assertThat(terminou.await(1, TimeUnit.SECONDS)).isTrue()
        }
        assertThat(chamadas.get()).isEqualTo(1)
    }

    @Test
    fun `timeout e seguido de nova tentativa de leitura`() {
        val chamadas = AtomicInteger()
        val historico = HistoricoFake { _, instante, _ ->
            assertThat(instante).isEqualTo(referencia)
            if (chamadas.incrementAndGet() == 1) CountDownLatch(1).await()
            dados
        }
        resiliente(historico, timeout = Duration.ofMillis(200), tentativas = 2).use {
            assertThat(it.consultar(modalidade, referencia, true)).isEqualTo(dados)
        }
        assertThat(chamadas.get()).isEqualTo(2)
    }

    @Test
    fun `leituras que ignoram interrupcao nao liberam capacidade nem executam tarefa expirada na fila`() {
        val iniciadas = CountDownLatch(4)
        val liberar = CountDownLatch(1)
        val terminadas = CountDownLatch(4)
        val chamadas = AtomicInteger()
        val historico = HistoricoFake { _, _, _ ->
            chamadas.incrementAndGet()
            iniciadas.countDown()
            try {
                while (liberar.count > 0) {
                    try {
                        liberar.await()
                    } catch (_: InterruptedException) {
                        // Simula um driver que não termina ao ser interrompido.
                    }
                }
                dados
            } finally {
                terminadas.countDown()
            }
        }
        val chamadores = Executors.newFixedThreadPool(4)
        try {
            resiliente(historico, timeout = Duration.ofSeconds(2), tentativas = 1).use { porta ->
                val resultados = (1..4).map {
                    chamadores.submit<Throwable?> {
                        runCatching { porta.consultar(modalidade, referencia, true) }.exceptionOrNull()
                    }
                }
                assertThat(iniciadas.await(1500, TimeUnit.MILLISECONDS)).isTrue()
                resultados.forEach { assertThat(it.get(4, TimeUnit.SECONDS)).isInstanceOf(ConsultaCambialIndisponivel::class.java) }
                assertThrows<ConsultaCambialIndisponivel> { porta.consultar(modalidade, referencia, true) }
                assertThat(chamadas.get()).isEqualTo(4)
                liberar.countDown()
                assertThat(terminadas.await(3, TimeUnit.SECONDS)).isTrue()
                assertThat(chamadas.get()).isEqualTo(4)
            }
        } finally {
            liberar.countDown()
            chamadores.shutdownNow()
        }
    }

    @Test
    fun `falhas permanentes, escrita e consulta sem cambio nao sao repetidas`() {
        val chamadas = AtomicInteger()
        val historico = HistoricoFake { _, _, _ ->
            chamadas.incrementAndGet()
            throw DataAccessResourceFailureException("permanente")
        }
        resiliente(historico).use {
            assertThrows<DataAccessResourceFailureException> { it.consultar(modalidade, referencia, true) }
            assertThrows<DataAccessResourceFailureException> { it.consultar(modalidade, referencia, false) }
            it.incluir(cotacao)
        }
        assertThat(chamadas.get()).isEqualTo(2)
        assertThat(historico.escritas.get()).isEqualTo(1)
    }

    @Test
    fun `cotacao ausente ou expirada nao provoca retry nem fallback`() {
        listOf(dados.copy(cotacao = null), dados.copy(cotacao = cotacao.copy(vigenteEm = referencia.minusSeconds(1801))))
            .forEach { retorno ->
                val chamadas = AtomicInteger()
                val historico = HistoricoFake { _, _, _ -> chamadas.incrementAndGet(); retorno }
                resiliente(historico).use {
                    val servico = ManterEConsultarTaxas(it, SeletorTaxas(Duration.ofMinutes(30)), Clock.fixed(referencia, ZoneOffset.UTC))
                    assertThrows<DadosFinanceirosIndisponiveis> { servico.consultar(modalidade, true) }
                }
                assertThat(chamadas.get()).isEqualTo(1)
            }
    }

    @Test
    fun `rejeita consulta cambial dentro de transacao`() {
        val chamadas = AtomicInteger()
        val historico = HistoricoFake { _, _, _ -> chamadas.incrementAndGet(); dados }
        resiliente(historico).use {
            TransactionSynchronizationManager.setActualTransactionActive(true)
            try {
                assertThrows<IllegalStateException> { it.consultar(modalidade, referencia, true) }
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false)
            }
        }
        assertThat(chamadas.get()).isZero()
    }

    @Test
    fun `rejeita configuracao invalida`() {
        val historico = HistoricoFake { _, _, _ -> dados }
        listOf(Duration.ZERO, Duration.ofNanos(1), Duration.ofSeconds(31)).forEach { timeout ->
            assertThrows<IllegalArgumentException> { resiliente(historico, timeout = timeout) }
        }
        listOf(0, 6).forEach { tentativas ->
            assertThrows<IllegalArgumentException> { resiliente(historico, tentativas = tentativas) }
        }
        listOf(Duration.ofMillis(-1), Duration.ofNanos(1), Duration.ofSeconds(6)).forEach { atraso ->
            assertThrows<IllegalArgumentException> { resiliente(historico, atraso = atraso) }
        }
    }

    @Test
    fun `configuracao Spring invalida impede inicializacao`() {
        listOf(
            "srm.cambio.consulta.timeout=PT0S",
            "srm.cambio.consulta.tentativas=0",
            "srm.cambio.consulta.atraso=PT6S",
        ).forEach { propriedade ->
            ApplicationContextRunner()
                .withUserConfiguration(ConfiguracaoTaxas::class.java)
                .withBean(JdbcTemplate::class.java, { JdbcTemplate() })
                .withPropertyValues(propriedade)
                .run { context -> assertThat(context.startupFailure).hasRootCauseInstanceOf(IllegalArgumentException::class.java) }
        }
    }
}
