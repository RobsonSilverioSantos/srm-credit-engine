package br.com.srm.creditengine.usuarios.adaptador.entrada

import java.util.UUID

data class MinhaContaResposta(
    val id: UUID,
    val organizacaoId: UUID,
    val email: String,
    val perfil: String,
    val ativo: Boolean,
    val capacidades: CapacidadesUsuarioResposta,
)
