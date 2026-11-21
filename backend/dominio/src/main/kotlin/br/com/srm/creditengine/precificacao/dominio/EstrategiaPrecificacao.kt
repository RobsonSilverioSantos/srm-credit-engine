package br.com.srm.creditengine.precificacao.dominio

import java.math.BigDecimal

interface EstrategiaPrecificacao {
    val modalidade: String

    fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal
}
