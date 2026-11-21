package br.com.srm.creditengine.precificacao.dominio

import java.math.BigDecimal

object ChequePreDatado : EstrategiaPrecificacao {
    override val modalidade = Modalidades.CHEQUE_PRE_DATADO

    override fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal =
        CalculoValorPresente.calcular(dados, BigDecimal("0.025"))
}
