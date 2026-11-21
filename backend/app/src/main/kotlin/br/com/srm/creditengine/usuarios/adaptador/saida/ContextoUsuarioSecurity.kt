package br.com.srm.creditengine.usuarios.adaptador.saida

import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.ContextoUsuario
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import java.util.UUID
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
class ContextoUsuarioSecurity : ContextoUsuario {
    override fun identidade(): IdentidadeUsuario {
        val autenticacao = SecurityContextHolder.getContext().authentication
        check(autenticacao is JwtAuthenticationToken && autenticacao.isAuthenticated) {
            "Não há identidade autenticada validada nesta requisição"
        }
        return IdentidadeUsuario(
            usuarioId = UUID.fromString(autenticacao.token.subject),
            organizacaoId = UUID.fromString(autenticacao.token.getClaimAsString("organizacao_id")),
        )
    }
}
