package br.com.srm.creditengine.lotes.aplicacao

import java.util.UUID

data class ResultadoLiquidacaoLote(
    val loteId: UUID,
    val liquidadaEm: String,
    val itens: List<ItemLiquidadoLote>,
)
