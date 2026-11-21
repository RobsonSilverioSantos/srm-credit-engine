package br.com.srm.creditengine.liquidacao.aplicacao.porta.saida

import br.com.srm.creditengine.liquidacao.dominio.Liquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ResultadoOperacaoLiquidacao

fun interface TransacaoLiquidacao {
    fun confirmar(
        identidade: IdentidadeLiquidacao,
        chave: String,
        hash: ByteArray,
        liquidacao: Liquidacao,
    ): ResultadoOperacaoLiquidacao
}
