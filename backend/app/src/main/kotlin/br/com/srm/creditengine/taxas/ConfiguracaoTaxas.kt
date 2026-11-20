package br.com.srm.creditengine.taxas

import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoCambialResiliente
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import java.time.Clock
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["srm.taxas.enabled"], havingValue = "true", matchIfMissing = true)
class ConfiguracaoTaxas {
    @Bean(destroyMethod = "close")
    fun historicoCambial(
        jdbc: JdbcTemplate,
        @Value("\${srm.cambio.consulta.timeout:PT1S}") timeout: Duration,
        @Value("\${srm.cambio.consulta.tentativas:3}") tentativas: Int,
        @Value("\${srm.cambio.consulta.atraso:PT0.1S}") atraso: Duration,
    ): HistoricoCambialResiliente =
        HistoricoCambialResiliente(HistoricoFinanceiroJdbc(jdbc), timeout, tentativas, atraso)

    @Bean
    fun taxasPorta(
        historico: HistoricoCambialResiliente,
        @Value("\${srm.cambio.validade:PT30M}") validade: Duration,
    ): TaxasPorta =
        ManterEConsultarTaxas(historico, SeletorTaxas(validade), Clock.systemUTC())
}
