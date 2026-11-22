package br.com.srm.creditengine.lotes.aplicacao

import java.util.UUID

data class PreviaLiquidacaoLote(
    val loteId: UUID,
    val referencia: String,
    val aptoParaLiquidar: Boolean,
    val hashPrevia: String?,
    val itens: List<PreviaItemLiquidacao>,
    val totaisPorMoeda: Map<String, String>,
)
