package br.com.srm.creditengine.precificacao.dominio

import java.math.BigDecimal

class EstrategiaComSpreadVersionado(
    override val modalidade: String,
    private val spread: BigDecimal,
) : EstrategiaPrecificacao {
    override fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal =
        CalculoValorPresente.calcular(dados, spread)
}
