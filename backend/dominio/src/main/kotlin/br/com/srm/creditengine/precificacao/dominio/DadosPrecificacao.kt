package br.com.srm.creditengine.precificacao.dominio

import java.math.BigDecimal

data class DadosPrecificacao(
    val modalidade: String,
    val valorFace: BigDecimal,
    val taxaBase: BigDecimal,
    val prazoMeses: Int,
    val cotacaoBrlPorUsd: BigDecimal? = null,
) {
    init {
        require(valorFace.signum() > 0) { "Valor de face deve ser positivo" }
        require(valorFace.stripTrailingZeros().scale() <= 2) {
            "Valor de face deve ser representável em centavos"
        }
        require(taxaBase.signum() >= 0) { "Taxa base deve ser não negativa" }
        require(prazoMeses > 0) { "Prazo deve ser positivo" }
        require(cotacaoBrlPorUsd == null || cotacaoBrlPorUsd.signum() > 0) {
            "Cotação BRL/USD deve ser positiva"
        }
    }
}
