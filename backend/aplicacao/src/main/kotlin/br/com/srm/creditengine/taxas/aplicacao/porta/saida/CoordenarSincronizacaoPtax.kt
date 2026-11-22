package br.com.srm.creditengine.taxas.aplicacao.porta.saida

fun interface CoordenarSincronizacaoPtax {
    fun executar(sincronizar: () -> Int): Int
}
