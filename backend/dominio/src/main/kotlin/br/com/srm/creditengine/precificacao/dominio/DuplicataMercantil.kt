package br.com.srm.creditengine.precificacao.dominio

import java.math.BigDecimal

object DuplicataMercantil : EstrategiaPrecificacao {
    override val modalidade = Modalidades.DUPLICATA_MERCANTIL

    override fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal =
        CalculoValorPresente.calcular(dados, BigDecimal("0.015"))
}
