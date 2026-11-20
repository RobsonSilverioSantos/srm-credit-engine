package br.com.srm.creditengine.seguranca.adaptador.entrada

import br.com.srm.creditengine.seguranca.adaptador.saida.UsuarioJwtValidator
import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import com.nimbusds.jose.proc.SecurityContext
import com.nimbusds.jose.jwk.source.ImmutableSecret

@ConfigurationProperties("security.jwt")
data class JwtProperties(
    val secret: String,
    val issuer: String = "srm-credit-engine",
    val expiration: Duration = Duration.ofMinutes(15),
) {
    init {
        require(secret.toByteArray(StandardCharsets.UTF_8).size >= 32) {
            "SRM_JWT_SECRET deve conter pelo menos 32 bytes"
        }
        require(expiration.seconds > 0) {
            "A expiração do token deve ser positiva"
        }
    }

    fun secretKey(): SecretKey =
        SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")

    override fun toString(): String =
        "JwtProperties(issuer=$issuer, expiration=$expiration, secret=[redacted])"
}

@Configuration(proxyBeanMethods = false)
class JwtConfiguracao {
    @Bean
    fun jwtEncoder(properties: JwtProperties): JwtEncoder =
        NimbusJwtEncoder(ImmutableSecret<SecurityContext>(properties.secretKey()))

    @Bean
    fun jwtDecoder(properties: JwtProperties, usuarioJwtValidator: UsuarioJwtValidator): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(properties.secretKey())
            .macAlgorithm(MacAlgorithm.HS256)
            .build()
            .apply {
                setJwtValidator(
                    DelegatingOAuth2TokenValidator(
                        JwtValidators.createDefaultWithIssuer(properties.issuer),
                        usuarioJwtValidator,
                    ),
                )
            }
}
