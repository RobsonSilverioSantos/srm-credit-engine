package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.lotes.aplicacao.ItemLiquidacaoLote

data class PreviaLiquidacaoLoteEntrada(val itens: List<ItemLiquidacaoLote>)
