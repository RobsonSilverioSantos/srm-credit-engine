package br.com.srm.creditengine.taxas

import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class HistoricoFinanceiroPostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var portaSpring: TaxasPorta
    private val inicio = Instant.parse("2026-11-20T20:00:00Z")
    private val modalidade = Modalidades.DUPLICATA_MERCANTIL
    private fun servico(instante: Instant) = ManterEConsultarTaxas(
        HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), Clock.fixed(instante, ZoneOffset.UTC),
    )

    @Test
    fun `persiste versões imutáveis e seleciona somente registros conhecidos e vigentes`() {
        assertThat(portaSpring).isNotNull()
        val inicial = servico(inicio)
        val base1 = inicial.registrarTaxaBase(BigDecimal("0.01"), inicio)
        val spread1 = inicial.registrarSpread(modalidade, BigDecimal("0.015"), inicio)
        val futuro = inicio.plusSeconds(600)
        inicial.registrarCotacao(BigDecimal("5.5000"), futuro)
        assertThrows<DadosFinanceirosIndisponiveis> { inicial.consultar(modalidade, true) }
        assertThrows<IllegalArgumentException> { inicial.registrarCotacao(BigDecimal.ZERO, inicio) }
        val cotacao1 = inicial.registrarCotacao(BigDecimal("5.4321"), inicio)
        inicial.registrarTaxaBase(BigDecimal("0.02"), futuro)
        inicial.registrarSpread(modalidade, BigDecimal("0.025"), futuro)
        servico(futuro.plusSeconds(1)).registrarTaxaBase(BigDecimal("0.03"), inicio.plusSeconds(300))

        val antes = inicial.consultar(modalidade, true)
        assertThat(antes.taxaBase.id).isEqualTo(base1.id)
        assertThat(antes.spread.id).isEqualTo(spread1.id)
        assertThat(antes.cotacao?.id).isEqualTo(cotacao1.id)
        assertThat(antes.taxaBase.criadoEm).isEqualTo(inicio)
        assertThat(antes.cotacao?.vigenteEm).isEqualTo(inicio)
        val depois = servico(futuro).consultar(modalidade, true)
        assertThat(depois.taxaBase.taxaMensal).isEqualByComparingTo("0.02")
        assertThat(depois.spread.spreadMensal).isEqualByComparingTo("0.025")
        assertThat(depois.cotacao?.taxaBrlPorUsd).isEqualByComparingTo("5.5")
        assertThat(depois.referencia).isEqualTo(futuro)
        assertThat(servico(inicio.plusSeconds(300)).consultar(modalidade, true).taxaBase.id).isEqualTo(base1.id)
        assertThrows<DadosFinanceirosIndisponiveis> { servico(inicio.minusSeconds(1)).consultar(modalidade, true) }
        assertThrows<DadosFinanceirosIndisponiveis> { servico(futuro.plusSeconds(1801)).consultar(modalidade, true) }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM taxas_base", Int::class.java)).isEqualTo(3)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spreads_recebiveis", Int::class.java)).isEqualTo(2)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cotacoes_cambiais", Int::class.java)).isEqualTo(2)
        assertThrows<DataAccessException> { jdbc.update("UPDATE taxas_base SET taxa_mensal = 0.03") }
        assertThrows<DataAccessException> { jdbc.update("DELETE FROM spreads_recebiveis") }
        assertThrows<DataAccessException> { jdbc.update("DELETE FROM cotacoes_cambiais") }
    }
}
