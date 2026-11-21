package br.com.srm.creditengine.liquidacao.dominio

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ResultadoLiquidacao(
    val liquidacaoId: UUID,
    val recebivelId: UUID,
    val moeda: MoedaPagamento,
    val valorPresenteBrl: BigDecimal,
    val desagioBrl: BigDecimal,
    val valorPagamento: BigDecimal,
    val liquidadaEm: Instant,
)
