package br.com.srm.creditengine.precificacao.dominio

import java.math.BigDecimal
import java.math.RoundingMode

object CalculoValorPresente {
    fun calcular(dados: DadosPrecificacao, spread: BigDecimal): BigDecimal {
        require(spread.signum() >= 0) { "Spread deve ser não negativo" }
        val fator = BigDecimal.ONE.add(dados.taxaBase).add(spread).pow(dados.prazoMeses)
        return dados.valorFace.divide(fator, 2, RoundingMode.HALF_EVEN)
    }
}
