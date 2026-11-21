package br.com.srm.creditengine.usuarios.dominio

import java.util.UUID

data class UsuarioOrganizacional(
    val id: UUID,
    val organizacaoId: UUID,
    val email: String,
    val perfil: PerfilUsuario,
    val ativo: Boolean,
)
