package br.com.srm.creditengine.taxas.aplicacao

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class BoletimPtax(
    val compra: BigDecimal,
    val venda: BigDecimal,
    val referencia: Instant,
    val data: LocalDate,
    val horarioOriginal: String,
) {
    init {
        require(compra.signum() > 0 && venda >= compra) { "Compra/venda PTAX inválidas" }
        listOf(compra, venda).forEach {
            val n = it.stripTrailingZeros()
            require(n.scale() <= 10 && n.precision() - n.scale() <= 10) { "Precisão PTAX inválida" }
        }
    }
}
