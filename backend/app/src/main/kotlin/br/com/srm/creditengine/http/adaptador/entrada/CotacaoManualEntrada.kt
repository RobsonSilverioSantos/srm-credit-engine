package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema

data class CotacaoManualEntrada(
    @field:Schema(type = "string", example = "5.4321", pattern = "[0-9]{1,10}(\\.[0-9]{1,10})?")
    val taxaBrlPorUsd: String,
    @field:Schema(minLength = 1, maxLength = 1000) val justificativa: String,
)
