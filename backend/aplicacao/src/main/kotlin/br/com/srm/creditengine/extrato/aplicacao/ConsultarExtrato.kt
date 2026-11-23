package br.com.srm.creditengine.extrato.aplicacao

fun interface ConsultarExtrato {
    fun consultar(filtro: FiltroExtrato): PaginaExtrato
}
