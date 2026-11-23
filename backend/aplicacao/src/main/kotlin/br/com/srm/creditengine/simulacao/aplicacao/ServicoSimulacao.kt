package br.com.srm.creditengine.simulacao.aplicacao

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.precificacao.aplicacao.precificarComTaxas
import br.com.srm.creditengine.precificacao.aplicacao.RevisaoFinanceira
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.ComandoSimulacao
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.ResultadoSimulacao
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.SimularRecebivel
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta

class ServicoSimulacao(private val taxas: TaxasPorta) : SimularRecebivel {
    override fun simular(comando: ComandoSimulacao): ResultadoSimulacao {
        val snapshot = taxas.consultar(comando.modalidade, comando.moeda == MoedaPagamento.USD)
        val resultado = precificarComTaxas(comando.modalidade, comando.valorFaceBrl, comando.prazoMeses, snapshot)
        return ResultadoSimulacao(comando.moeda, resultado, snapshot, RevisaoFinanceira.calcular(
            comando.modalidade, comando.valorFaceBrl, comando.prazoMeses, comando.moeda, snapshot, resultado))
    }
}
