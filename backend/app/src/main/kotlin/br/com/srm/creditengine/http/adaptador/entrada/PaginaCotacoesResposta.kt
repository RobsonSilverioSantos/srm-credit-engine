package br.com.srm.creditengine.http.adaptador.entrada

data class PaginaCotacoesResposta(
    val itens: List<CotacaoCambialResposta>, val pagina: Int, val tamanho: Int, val total: Long,
)
