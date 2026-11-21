package br.com.srm.creditengine.seguranca.adaptador.entrada

import br.com.srm.creditengine.seguranca.adaptador.saida.UsuarioAutenticavel
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/auth")
class AutenticacaoController(
    private val authenticationManager: AuthenticationManager,
    private val jwtEncoder: JwtEncoder,
    private val properties: JwtProperties,
) {
    @PostMapping("/token")
    fun emitirToken(@RequestBody request: TokenRequest): TokenResponse {
        val authentication = try {
            authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken(request.email.trim(), request.password),
            )
        } catch (_: AuthenticationException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas")
        }
        val usuario = authentication.principal as? UsuarioAutenticavel
            ?: error("O provedor de autenticação retornou um principal inesperado")

        val emitidoEm = Instant.now()
        val expiraEm = emitidoEm.plus(properties.expiration)
        val claims = JwtClaimsSet.builder()
            .issuer(properties.issuer)
            .subject(usuario.id.toString())
            .issuedAt(emitidoEm)
            .expiresAt(expiraEm)
            .claim("organizacao_id", usuario.organizacaoId.toString())
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue

        return TokenResponse(token, "Bearer", properties.expiration.seconds)
    }
}
