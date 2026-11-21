package br.com.srm.creditengine.liquidacao.aplicacao.porta.saida

import br.com.srm.creditengine.liquidacao.dominio.Liquidacao

interface PersistenciaLiquidacao {
    fun atualizarRecebivel(liquidacao: Liquidacao): Boolean
    fun inserir(liquidacao: Liquidacao)
}
