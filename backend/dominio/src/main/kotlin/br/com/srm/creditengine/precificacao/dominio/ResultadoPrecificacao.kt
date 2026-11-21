package br.com.srm.creditengine.precificacao.dominio

import java.math.BigDecimal

data class ResultadoPrecificacao(
    val valorPresenteBrl: BigDecimal,
    val desagioBrl: BigDecimal,
    val valorPresenteUsd: BigDecimal?,
)
