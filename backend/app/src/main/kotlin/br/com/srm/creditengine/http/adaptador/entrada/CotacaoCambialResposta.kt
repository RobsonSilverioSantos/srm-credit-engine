package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class CotacaoCambialResposta(
    val id: UUID,
    @field:Schema(type = "string", example = "5.4321000000") val taxaBrlPorUsd: String,
    val moedaBase: String,
    val moedaCotada: String,
    val fonte: String,
    val lado: String,
    val versao: Int,
    val referencia: String,
    val observadaEm: String,
    val dataBoletim: String?,
    val horarioOriginal: String?,
    val boletim: String?,
    val fuso: String?,
    @field:Schema(description = "Visível somente para MASTER/ADMIN") val autorId: UUID?,
    @field:Schema(description = "Visível somente para MASTER/ADMIN") val justificativa: String?,
)
