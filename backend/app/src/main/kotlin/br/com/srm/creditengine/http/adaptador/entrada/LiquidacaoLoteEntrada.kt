package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.lotes.aplicacao.ItemLiquidacaoLote

data class LiquidacaoLoteEntrada(
    val itens: List<ItemLiquidacaoLote>,
    val hashPrevia: String,
    val confirmacaoFinanceira: Boolean,
)
