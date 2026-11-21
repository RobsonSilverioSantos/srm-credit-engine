package br.com.srm.creditengine.usuarios

import br.com.srm.creditengine.usuarios.adaptador.saida.UsuariosJdbc
import br.com.srm.creditengine.usuarios.aplicacao.ServicoGestaoUsuarios
import br.com.srm.creditengine.usuarios.aplicacao.ServicoProvisionarMaster
import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.GestaoUsuarios
import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.ProvisionarMaster
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.CodificadorSenha
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.ContextoUsuario
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.RepositorioUsuarios
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration(proxyBeanMethods = false)
class UsuariosConfiguracao {
    @Bean
    fun repositorioUsuarios(jdbc: JdbcTemplate): RepositorioUsuarios = UsuariosJdbc(jdbc)

    @Bean
    fun codificadorSenha(passwordEncoder: PasswordEncoder) =
        CodificadorSenha { senha -> checkNotNull(passwordEncoder.encode(senha)) }

    @Bean
    fun gestaoUsuarios(
        contexto: ContextoUsuario,
        usuarios: RepositorioUsuarios,
        codificador: CodificadorSenha,
    ): GestaoUsuarios = ServicoGestaoUsuarios(contexto, usuarios, codificador)

    @Bean
    fun provisionarMaster(
        usuarios: RepositorioUsuarios,
        codificador: CodificadorSenha,
    ): ProvisionarMaster = ServicoProvisionarMaster(usuarios, codificador)
}
