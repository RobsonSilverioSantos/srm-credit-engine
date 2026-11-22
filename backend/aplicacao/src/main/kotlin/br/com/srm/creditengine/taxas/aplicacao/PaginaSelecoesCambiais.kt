package br.com.srm.creditengine.taxas.aplicacao

data class PaginaSelecoesCambiais(
    val itens: List<SelecaoFonteCambial>, val pagina: Int, val tamanho: Int, val total: Long,
)
