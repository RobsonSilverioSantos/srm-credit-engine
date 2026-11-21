package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class LiquidacaoResposta(
    val liquidacaoId: UUID, val recebivelId: UUID, val moeda: String,
    @field:Schema(example = "92859.94") val valorPresenteBrl: String,
    @field:Schema(example = "7140.06") val desagioBrl: String,
    @field:Schema(example = "17094.67") val valorPagamento: String,
    @field:Schema(example = "2026-11-20T12:00:00Z") val liquidadaEm: String,
)
