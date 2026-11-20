package br.com.srm.creditengine.cadastro

import br.com.srm.creditengine.cadastro.adaptador.saida.CedentesJdbc
import br.com.srm.creditengine.cadastro.adaptador.saida.RecebiveisJdbc
import br.com.srm.creditengine.cadastro.aplicacao.ServicoCadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import javax.sql.DataSource
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration::class, JdbcTemplateAutoConfiguration::class)
@ConditionalOnBean(value = [ContextoOrganizacao::class, JdbcTemplate::class, DataSource::class])
class CadastroAutoConfiguracao {
    @Bean
    fun cadastro(jdbc: JdbcTemplate, contexto: ContextoOrganizacao): Cadastro =
        ServicoCadastro(contexto, CedentesJdbc(jdbc), RecebiveisJdbc(jdbc))
}
