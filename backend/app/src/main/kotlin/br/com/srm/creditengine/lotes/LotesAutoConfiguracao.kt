package br.com.srm.creditengine.lotes

import br.com.srm.creditengine.cadastro.adaptador.saida.CedentesJdbc
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
