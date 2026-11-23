package br.com.srm.creditengine.seguranca.adaptador.entrada

import java.nio.charset.StandardCharsets
import java.time.Duration
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.springframework.boot.context.properties.ConfigurationProperties

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
