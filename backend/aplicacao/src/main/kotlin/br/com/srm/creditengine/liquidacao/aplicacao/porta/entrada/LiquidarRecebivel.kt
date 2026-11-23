package br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada

fun interface LiquidarRecebivel {
    fun liquidar(comando: ComandoLiquidacao): ResultadoOperacaoLiquidacao
}
