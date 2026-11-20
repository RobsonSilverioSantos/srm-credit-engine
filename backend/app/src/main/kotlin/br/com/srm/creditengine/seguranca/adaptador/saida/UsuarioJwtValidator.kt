package br.com.srm.creditengine.seguranca.adaptador.saida

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
class UsuarioJwtValidator(
    private val jdbc: JdbcTemplate,
) : OAuth2TokenValidator<Jwt> {
    override fun validate(jwt: Jwt): OAuth2TokenValidatorResult {
        val usuarioId = uuid(jwt.subject)
        val organizacaoId = uuid(jwt.getClaimAsString("organizacao_id"))
        if (usuarioId == null || organizacaoId == null) return invalidToken()

        val ativo = jdbc.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM usuarios u
                JOIN organizacoes o ON o.id = u.organizacao_id
                WHERE u.id = ? AND u.organizacao_id = ?
                  AND u.ativo = TRUE AND o.ativa = TRUE
            )
            """.trimIndent(),
            Boolean::class.java,
            usuarioId,
            organizacaoId,
        ) == true

        return if (ativo) OAuth2TokenValidatorResult.success() else invalidToken()
    }

    private fun uuid(value: String?): UUID? =
        try {
            value?.let(UUID::fromString)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun invalidToken(): OAuth2TokenValidatorResult =
        OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", "Token inválido", null))
}
