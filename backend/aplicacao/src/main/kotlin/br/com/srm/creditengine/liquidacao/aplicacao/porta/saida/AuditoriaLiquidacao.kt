package br.com.srm.creditengine.liquidacao.aplicacao.porta.saida

import br.com.srm.creditengine.liquidacao.dominio.Liquidacao

fun interface AuditoriaLiquidacao {
    fun registrar(identidade: IdentidadeLiquidacao, liquidacao: Liquidacao)
}
