package br.com.srm.creditengine.lotes.aplicacao

import java.util.UUID

data class ItemLiquidadoLote(
    val liquidacaoId: UUID,
    val recebivelId: UUID,
    val moedaPagamento: String,
    val valorPresenteBrl: String,
    val desagioBrl: String,
    val valorPagamento: String,
)
