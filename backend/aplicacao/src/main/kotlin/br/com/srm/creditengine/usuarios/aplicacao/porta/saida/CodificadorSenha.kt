package br.com.srm.creditengine.usuarios.aplicacao.porta.saida

fun interface CodificadorSenha {
    fun codificar(senha: String): String
}
