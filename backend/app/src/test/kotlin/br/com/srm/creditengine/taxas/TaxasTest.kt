package br.com.srm.creditengine.taxas

import br.com.srm.creditengine.precificacao.dominio.Modalidades
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TaxasTest {
    private val agora = Instant.parse("2026-11-20T20:00:00Z")
    private val modalidade = Modalidades.DUPLICATA_MERCANTIL
    private val base = TaxaBase(UUID.randomUUID(), BigDecimal("0.01"), agora, agora)
    private val spread = SpreadRecebivel(UUID.randomUUID(), modalidade, BigDecimal("0.015"), agora, agora)
    private val cotacao = CotacaoCambial(UUID.randomUUID(), BigDecimal("5.4321"), agora, agora)
    private val seletor = SeletorTaxas(Duration.ofMinutes(30))

    @Test
    fun `verifica elegibilidade, limite inclusivo e dados para snapshot`() {
        val resultado = seletor.selecionar(modalidade, agora.plusSeconds(1800), true, HistoricoElegivel(base, spread, cotacao))
        assertThat(resultado.taxaBase).isEqualTo(base)
        assertThat(resultado.spread).isEqualTo(spread)
        assertThat(resultado.cotacao).isEqualTo(cotacao)
        assertThat(resultado.cotacaoValidaAte).isEqualTo(agora.plusSeconds(1800))
        assertThrows<DadosFinanceirosIndisponiveis> {
            seletor.selecionar(modalidade, agora.plusSeconds(1801), true, HistoricoElegivel(base, spread, cotacao))
        }
        assertThat(seletor.selecionar(modalidade, agora, false, HistoricoElegivel(base, spread, null)).cotacao).isNull()
    }

    @Test
    fun `rejeita dados futuros, ausentes ou de outra modalidade`() {
        val futuro = agora.plusSeconds(1)
        val registros = listOf(
            HistoricoElegivel(base.copy(vigenteEm = futuro), spread, cotacao),
            HistoricoElegivel(base.copy(criadoEm = futuro), spread, cotacao),
            HistoricoElegivel(base, spread.copy(vigenteEm = futuro), cotacao),
            HistoricoElegivel(base, spread, cotacao.copy(vigenteEm = futuro)),
            HistoricoElegivel(base, spread, cotacao.copy(criadoEm = futuro)),
            HistoricoElegivel(null, spread, cotacao),
            HistoricoElegivel(base, null, cotacao),
            HistoricoElegivel(base, spread, null),
            HistoricoElegivel(base, spread.copy(modalidade = Modalidades.CHEQUE_PRE_DATADO), cotacao),
        )
        registros.forEach {
            assertThrows<DadosFinanceirosIndisponiveis> { seletor.selecionar(modalidade, agora, true, it) }
        }
    }

    @Test
    fun `rejeita valores inválidos ou que PostgreSQL arredondaria`() {
        listOf("0", "-1", "0.00000000001", "10000000000").forEach {
            assertThrows<IllegalArgumentException> { cotacao.copy(taxaBrlPorUsd = BigDecimal(it)) }
        }
        listOf("-1", "100", "0.00000000001").forEach {
            assertThrows<IllegalArgumentException> { base.copy(taxaMensal = BigDecimal(it)) }
            assertThrows<IllegalArgumentException> { spread.copy(spreadMensal = BigDecimal(it)) }
        }
        assertThrows<IllegalArgumentException> { SeletorTaxas(Duration.ZERO) }
        assertThrows<IllegalArgumentException> { SeletorTaxas(Duration.ofNanos(1)) }
    }

    @Test
    fun `captura uma única referência UTC mesmo com relógio mudando`() {
        var leituras = 0
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.ofHours(-3)
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = agora.plusSeconds(leituras++.toLong())
        }
        val porta = object : HistoricoFinanceiroPorta {
            override fun incluir(taxa: TaxaBase) = error("não usado")
            override fun incluir(spread: SpreadRecebivel) = error("não usado")
            override fun incluir(cotacao: CotacaoCambial) = error("não usado")
            override fun consultar(modalidade: String, referencia: Instant, incluirCotacao: Boolean): HistoricoElegivel {
                assertThat(referencia).isEqualTo(agora)
                return HistoricoElegivel(base, spread, cotacao)
            }
        }
        val resultado = ManterEConsultarTaxas(porta, seletor, clock).consultar(modalidade, true)
        assertThat(resultado.referencia).isEqualTo(agora)
        assertThat(leituras).isEqualTo(1)
    }
}
