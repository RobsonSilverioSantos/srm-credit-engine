package br.com.srm.creditengine.extrato.adaptador.entrada

data class PaginaExtratoResposta(val itens: List<ItemExtratoResposta>, val pagina: Int, val tamanho: Int, val total: Long)
