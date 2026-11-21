package br.com.srm.creditengine.extrato.aplicacao

data class PaginaExtrato(val itens: List<ItemExtrato>, val pagina: Int, val tamanho: Int, val total: Long)
