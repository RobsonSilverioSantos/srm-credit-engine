package br.com.srm.creditengine.extrato

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.extrato.adaptador.saida.ExtratoJdbc
import br.com.srm.creditengine.extrato.aplicacao.ConsultarExtrato
import br.com.srm.creditengine.extrato.aplicacao.ServicoExtrato
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfiguration
@AutoConfigureAfter(JdbcTemplateAutoConfiguration::class)
class ExtratoConfiguracao {
    @Bean
    @ConditionalOnBean(value = [JdbcTemplate::class, ContextoOrganizacao::class])
    fun consultarExtrato(jdbc: JdbcTemplate, contexto: ContextoOrganizacao): ConsultarExtrato =
        ServicoExtrato(contexto, ExtratoJdbc(jdbc))
}
