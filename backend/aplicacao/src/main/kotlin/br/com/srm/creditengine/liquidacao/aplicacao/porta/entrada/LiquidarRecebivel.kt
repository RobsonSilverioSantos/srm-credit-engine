package br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.liquidacao.dominio.ResultadoLiquidacao
import java.util.UUID

data class ComandoLiquidacao(val recebivelId: UUID, val moeda: MoedaPagamento, val chaveIdempotencia: String) {
    init {
        require(chaveIdempotencia.isNotBlank() && chaveIdempotencia.length <= 255) {
            "Chave de idempotência obrigatória e limitada a 255 caracteres"
        }
    }
}

fun interface LiquidarRecebivel {
    fun liquidar(comando: ComandoLiquidacao): ResultadoLiquidacao
}
