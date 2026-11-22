package br.com.srm.creditengine.http.adaptador.entrada

data class LoteRecebiveisPreviaHttp(val confirmavel: Boolean, val itens: List<LoteRecebivelLinhaHttp>)
