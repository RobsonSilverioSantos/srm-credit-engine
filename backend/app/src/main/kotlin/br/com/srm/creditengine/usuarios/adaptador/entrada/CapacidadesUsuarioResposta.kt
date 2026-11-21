package br.com.srm.creditengine.usuarios.adaptador.entrada

data class CapacidadesUsuarioResposta(
    val perfisCriaveis: List<String>,
    val podeListarUsuarios: Boolean,
    val podeAlterarStatus: Boolean,
)
