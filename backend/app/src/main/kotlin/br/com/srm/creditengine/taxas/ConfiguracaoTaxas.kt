package br.com.srm.creditengine.taxas

import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoCambialResiliente
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.aplicacao.ServicoCambio
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoCambioJdbc
import br.com.srm.creditengine.taxas.adaptador.saida.PtaxODataHttp
import br.com.srm.creditengine.taxas.adaptador.saida.CoordenacaoPtaxJdbc
import javax.sql.DataSource
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.ContextoUsuario
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.RepositorioUsuarios
import java.net.URI
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import java.time.Clock
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["srm.taxas.enabled"], havingValue = "true", matchIfMissing = true)
class ConfiguracaoTaxas {
    @Bean
    fun historicoCambio(
        jdbc: JdbcTemplate, gerenciador: PlatformTransactionManager,
        @Value("\${srm.cambio.modo-demo:false}") demo: Boolean,
        @Value("\${srm.cambio.validade:PT30M}") validade: Duration,
    ) = HistoricoCambioJdbc(jdbc, TransactionTemplate(gerenciador), modoDemo = demo, validadeDemo = validade)

    @Bean
    fun ptax(
        json: ObjectMapper,
        @Value("\${srm.cambio.ptax.url:https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/}") url: URI,
        @Value("\${srm.cambio.ptax.timeout:PT3S}") timeout: Duration,
        @Value("\${srm.cambio.ptax.tentativas:3}") tentativas: Int,
        @Value("\${srm.cambio.ptax.atraso:PT0.1S}") atraso: Duration,
    ) = PtaxODataHttp(json, url, timeout, tentativas, atraso)

    @Bean
    fun coordenacaoPtax(dataSource: DataSource) = CoordenacaoPtaxJdbc(dataSource)

    @Bean
    fun gestaoCambio(
        contexto: ContextoUsuario, historico: HistoricoCambioJdbc, ptax: PtaxODataHttp, usuarios: RepositorioUsuarios,
        coordenacao: CoordenacaoPtaxJdbc,
    ) = ServicoCambio(contexto, historico, ptax, Clock.systemUTC(), usuarios, coordenacao)

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
        cambio: ServicoCambio,
    ): TaxasPorta =
        ManterEConsultarTaxas(historico, SeletorTaxas(validade), Clock.systemUTC(), cambio)
}
