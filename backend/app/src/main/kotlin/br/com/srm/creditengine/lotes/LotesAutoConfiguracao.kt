package br.com.srm.creditengine.lotes

import br.com.srm.creditengine.cadastro.adaptador.saida.CedentesJdbc
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.lotes.adaptador.saida.LiquidacaoLoteJdbc
import br.com.srm.creditengine.lotes.aplicacao.ServicoLiquidacaoLote
import br.com.srm.creditengine.lotes.aplicacao.porta.entrada.LiquidarLote
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import br.com.srm.creditengine.taxas.aplicacao.ServicoCambio
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoCambioJdbc
import java.time.Clock
import java.time.Duration
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import tools.jackson.databind.ObjectMapper
import br.com.srm.creditengine.lotes.adaptador.saida.LotesJdbc
import br.com.srm.creditengine.lotes.aplicacao.ServicoImportacaoLote
import br.com.srm.creditengine.lotes.aplicacao.porta.entrada.ImportarRecebiveis
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.SimularRecebivel
import br.com.srm.creditengine.taxas.adaptador.saida.ConsultaCambialIndisponivel
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.ContextoUsuario
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@AutoConfiguration
@AutoConfigureAfter(DataSourceTransactionManagerAutoConfiguration::class, JdbcTemplateAutoConfiguration::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(value = [JdbcTemplate::class, PlatformTransactionManager::class,
    SimularRecebivel::class, ContextoUsuario::class])
class LotesAutoConfiguracao {
    @Bean
    fun liquidarLote(
        jdbc: JdbcTemplate,
        transacao: PlatformTransactionManager,
        identidade: IdentidadeConfiavel,
        json: ObjectMapper,
        metricas: MeterRegistry,
        cambio: ServicoCambio,
        historicoCambio: HistoricoCambioJdbc,
        @Value("\${srm.lotes.max-linhas:200}") maxTitulos: Int,
        @Value("\${srm.cambio.validade:PT30M}") validade: Duration,
    ): LiquidarLote {
        val relogio = Clock.systemUTC()
        val logger = LoggerFactory.getLogger(LotesAutoConfiguracao::class.java)
        val persistencia = LiquidacaoLoteJdbc(jdbc, TransactionTemplate(transacao), relogio, json,
            aposCommit = { quantidade ->
                metricas.counter("srm.lotes.liquidacoes.confirmadas").increment()
                metricas.counter("srm.liquidacoes.confirmadas").increment(quantidade.toDouble())
                logger.atInfo().addKeyValue("event", "lote.liquidacao.confirmada")
                    .addKeyValue("quantidade", quantidade).log("Liquidação integral do lote confirmada após commit")
            },
            aposReplay = { metricas.counter("srm.lotes.liquidacoes.replays").increment() },
            historicoCambio = historicoCambio,
        )
        // Revalidação local JDBC: não usar o executor/retry cambial fora da conexão transacional.
        val taxas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(validade), relogio, cambio)
        return ServicoLiquidacaoLote(identidade, CedentesJdbc(jdbc), persistencia, taxas, relogio, maxTitulos)
    }

    @Bean
    fun importarRecebiveis(
        jdbc: JdbcTemplate,
        transacao: PlatformTransactionManager,
        contexto: ContextoUsuario,
        simulacoes: SimularRecebivel,
        @Value("\${srm.lotes.max-linhas:200}") maxLinhas: Int,
    ): ImportarRecebiveis = ServicoImportacaoLote(
        contexto, CedentesJdbc(jdbc), LotesJdbc(jdbc, TransactionTemplate(transacao)),
        SimularRecebivel { comando ->
            try {
                simulacoes.simular(comando)
            } catch (_: ConsultaCambialIndisponivel) {
                throw DadosFinanceirosIndisponiveis("Câmbio indisponível para simulação.")
            }
        },
        maxLinhas,
    )
}
