package br.com.srm.creditengine.seguranca.adaptador.entrada

import br.com.srm.creditengine.seguranca.adaptador.saida.UsuarioJwtValidator
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
