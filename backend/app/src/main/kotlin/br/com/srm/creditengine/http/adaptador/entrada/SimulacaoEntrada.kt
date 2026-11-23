package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema

data class SimulacaoEntrada(
    @field:Schema(allowableValues = ["DUPLICATA_MERCANTIL", "CHEQUE_PRE_DATADO"], requiredMode = Schema.RequiredMode.REQUIRED)
    val modalidade: String,
    @field:Schema(example = "100000.00", description = "BRL em string decimal", requiredMode = Schema.RequiredMode.REQUIRED)
    val valorFaceBrl: String,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val prazoMeses: Int,
    @field:Schema(allowableValues = ["BRL", "USD"], requiredMode = Schema.RequiredMode.REQUIRED) val moeda: String,
)
