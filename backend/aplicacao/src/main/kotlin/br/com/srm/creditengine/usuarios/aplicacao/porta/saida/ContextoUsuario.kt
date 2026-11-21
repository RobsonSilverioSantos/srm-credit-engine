package br.com.srm.creditengine.usuarios.aplicacao.porta.saida

import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario

fun interface ContextoUsuario {
    fun identidade(): IdentidadeUsuario
}
