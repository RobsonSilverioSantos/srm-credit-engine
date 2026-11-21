package br.com.srm.creditengine.usuarios.dominio

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
