package br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import java.util.UUID

data class ComandoLiquidacao(
    val recebivelId: UUID, val moeda: MoedaPagamento, val chaveIdempotencia: String, val revisaoCambial: String? = null,
) {
    init {
        require(chaveIdempotencia.isNotBlank() && chaveIdempotencia.length <= 255) {
            "Chave de idempotência obrigatória e limitada a 255 caracteres"
        }
        require(revisaoCambial == null || revisaoCambial.matches(Regex("[0-9a-f]{64}"))) { "Revisão cambial inválida" }
    }
}
