package br.com.srm.creditengine.seguranca.adaptador.entrada

data class TokenResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
)
