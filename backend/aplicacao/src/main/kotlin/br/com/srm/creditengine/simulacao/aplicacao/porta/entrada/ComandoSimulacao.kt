package br.com.srm.creditengine.simulacao.aplicacao.porta.entrada

import br.com.srm.creditengine.cadastro.dominio.validarDadosRecebivel
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import java.math.BigDecimal

data class ComandoSimulacao(
    val modalidade: String,
    val valorFaceBrl: BigDecimal,
    val prazoMeses: Int,
    val moeda: MoedaPagamento,
) {
    init { validarDadosRecebivel(modalidade, valorFaceBrl, prazoMeses) }
}
