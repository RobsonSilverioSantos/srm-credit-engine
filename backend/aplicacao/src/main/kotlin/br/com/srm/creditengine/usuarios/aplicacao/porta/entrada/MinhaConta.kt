package br.com.srm.creditengine.usuarios.aplicacao.porta.entrada

import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional

data class MinhaConta(
    val usuario: UsuarioOrganizacional,
    val perfisCriaveis: List<PerfilUsuario>,
    val podeListarUsuarios: Boolean,
    val podeAlterarStatus: Boolean,
)
