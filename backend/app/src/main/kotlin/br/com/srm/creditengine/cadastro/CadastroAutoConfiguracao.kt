package br.com.srm.creditengine.cadastro

import br.com.srm.creditengine.cadastro.adaptador.saida.CedentesJdbc
import br.com.srm.creditengine.cadastro.adaptador.saida.RecebiveisJdbc
import br.com.srm.creditengine.cadastro.aplicacao.ServicoCadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate

@AutoConfiguration
@ConditionalOnBean(ContextoOrganizacao::class)
class CadastroAutoConfiguracao {
    @Bean
    fun cadastro(jdbc: JdbcTemplate, contexto: ContextoOrganizacao): Cadastro =
        ServicoCadastro(contexto, CedentesJdbc(jdbc), RecebiveisJdbc(jdbc))
}
