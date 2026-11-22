package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema

data class SelecaoCambialEntrada(
    @field:Schema(allowableValues = ["BCB_PTAX", "MANUAL"]) val fonte: String,
    @field:Schema(nullable = true, description = "Obrigatória somente para autorizar MANUAL") val cotacaoId: String?,
    @field:Schema(minLength = 1, maxLength = 1000) val justificativa: String,
)
