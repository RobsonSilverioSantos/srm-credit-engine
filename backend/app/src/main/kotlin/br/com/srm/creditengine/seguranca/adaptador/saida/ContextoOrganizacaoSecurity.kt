package br.com.srm.creditengine.seguranca.adaptador.saida

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeLiquidacao
import java.util.UUID
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ContextoOrganizacaoSecurity : ContextoOrganizacao, IdentidadeConfiavel {
    private fun autenticacao(): JwtAuthenticationToken {
        val autenticacao = SecurityContextHolder.getContext().authentication
        check(autenticacao is JwtAuthenticationToken && autenticacao.isAuthenticated) {
            "Não há identidade autenticada validada nesta requisição"
        }
        return autenticacao
    }

    override fun organizacaoId(): UUID =
        UUID.fromString(autenticacao().token.getClaimAsString("organizacao_id"))

    override fun identidade(): IdentidadeLiquidacao {
        val token = autenticacao().token
        return IdentidadeLiquidacao(UUID.fromString(token.getClaimAsString("organizacao_id")), UUID.fromString(token.subject))
    }
}
