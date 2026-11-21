package br.com.srm.creditengine.usuarios.aplicacao.porta.entrada

import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario

data class NovoUsuarioOrganizacional(
    val email: String,
    val senha: String,
    val perfil: PerfilUsuario,
)
