package br.com.srm.creditengine.usuarios.dominio

import java.util.UUID

enum class PerfilUsuario {
    MASTER,
    ADMIN,
    OPERADOR,
    ;

    fun perfisCriaveis(): Set<PerfilUsuario> = when (this) {
        MASTER -> setOf(ADMIN, OPERADOR)
        ADMIN -> setOf(OPERADOR)
        OPERADOR -> emptySet()
    }

    fun perfisGerenciaveis(): Set<PerfilUsuario> = when (this) {
        MASTER -> setOf(ADMIN, OPERADOR)
        ADMIN -> setOf(OPERADOR)
        OPERADOR -> emptySet()
    }
}

data class UsuarioOrganizacional(
    val id: UUID,
    val organizacaoId: UUID,
    val email: String,
    val perfil: PerfilUsuario,
    val ativo: Boolean,
)

data class IdentidadeUsuario(
    val usuarioId: UUID,
    val organizacaoId: UUID,
)
