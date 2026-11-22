package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema

data class SimulacaoResposta(
    val moeda: String,
    @field:Schema(example = "92859.94") val valorPresenteBrl: String,
    @field:Schema(example = "7140.06") val desagioBrl: String,
    @field:Schema(example = "17094.67") val valorPagamento: String,
    @field:Schema(example = "0.0100000000") val taxaBaseMensal: String,
    @field:Schema(example = "0.0150000000") val spreadMensal: String,
    @field:Schema(example = "5.4321000000", nullable = true) val cotacaoBrlPorUsd: String?,
    val referencia: String,
    @field:Schema(nullable = true) val cotacaoValidaAte: String?,
    val fonteCambial: String? = null,
    @field:Schema(description = "Revisão financeira completa (dados, taxas, preço e câmbio). Reenviar na confirmação; obrigatória em USD PTAX/MANUAL.")
    val revisaoCambial: String? = null,
)
