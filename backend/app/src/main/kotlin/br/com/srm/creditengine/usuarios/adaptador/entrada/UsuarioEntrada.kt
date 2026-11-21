package br.com.srm.creditengine.usuarios.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema

data class UsuarioEntrada(
    val email: String,
    @field:Schema(description = "Senha de 12 a 72 bytes UTF-8")
    val senha: String,
    @field:Schema(allowableValues = ["ADMIN", "OPERADOR"])
    val perfil: String,
)
