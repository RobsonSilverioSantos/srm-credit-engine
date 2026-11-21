package br.com.srm.creditengine.usuarios.dominio

import java.util.UUID

data class IdentidadeUsuario(
    val usuarioId: UUID,
    val organizacaoId: UUID,
)
