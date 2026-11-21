package br.com.srm.creditengine.simulacao.aplicacao.porta.entrada

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.precificacao.dominio.ResultadoPrecificacao
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot

data class ResultadoSimulacao(
    val moeda: MoedaPagamento,
    val precificacao: ResultadoPrecificacao,
    val taxas: TaxasParaSnapshot,
)
