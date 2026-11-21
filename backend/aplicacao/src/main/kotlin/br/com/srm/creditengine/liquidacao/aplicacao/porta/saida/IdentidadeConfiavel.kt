package br.com.srm.creditengine.liquidacao.aplicacao.porta.saida

fun interface IdentidadeConfiavel {
    fun identidade(): IdentidadeLiquidacao
}
