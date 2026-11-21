package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema

data class LiquidacaoEntrada(@field:Schema(allowableValues = ["BRL", "USD"]) val moeda: String)
