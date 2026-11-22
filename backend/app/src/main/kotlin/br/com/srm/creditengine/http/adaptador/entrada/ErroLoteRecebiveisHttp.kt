package br.com.srm.creditengine.http.adaptador.entrada

data class ErroLoteRecebiveisHttp(val codigo: String, val mensagem: String, val itens: List<LoteRecebivelLinhaHttp>)
