package br.com.srm.creditengine.lotes.aplicacao

import java.util.UUID

data class PreviaItemLiquidacao(
    val recebivelId: UUID,
    val versao: Long,
    val estado: String,
    val moedaPagamento: String,
    val valorFaceBrl: String,
    val valorPresenteBrl: String? = null,
    val desagioBrl: String? = null,
    val valorPagamento: String? = null,
    val taxaBaseId: UUID? = null,
    val taxaBaseMensal: String? = null,
    val taxaBaseVigenteEm: String? = null,
    val spreadId: UUID? = null,
    val spreadMensal: String? = null,
    val spreadVigenteEm: String? = null,
    val cotacaoId: UUID? = null,
    val taxaBrlPorUsd: String? = null,
    val cotacaoVigenteEm: String? = null,
    val cotacaoValidaAte: String? = null,
    val fonteCambio: String? = null,
    val impedimento: String? = null,
)
