package br.com.srm.creditengine.usuarios.adaptador.entrada

import java.util.UUID

data class UsuarioResposta(
    val id: UUID,
    val email: String,
    val perfil: String,
    val ativo: Boolean,
)
