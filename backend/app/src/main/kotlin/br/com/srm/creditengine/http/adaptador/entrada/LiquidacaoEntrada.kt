package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema

data class LiquidacaoEntrada(
    @field:Schema(allowableValues = ["BRL", "USD"]) val moeda: String,
    @field:Schema(nullable = true, description = "Obrigatória em USD PTAX/MANUAL: revisão devolvida na simulação")
    val revisaoCambial: String? = null,
)
