package br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada

import br.com.srm.creditengine.liquidacao.dominio.ResultadoLiquidacao

/** O metadado é transportado sem interpretação pela aplicação. */
data class ResultadoOperacaoLiquidacao(
    val liquidacao: ResultadoLiquidacao,
    val metadado: Int,
)
