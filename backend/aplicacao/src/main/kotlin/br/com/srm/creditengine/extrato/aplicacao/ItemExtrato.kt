package br.com.srm.creditengine.extrato.aplicacao

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ItemExtrato(
    val liquidacaoId: UUID,
    val recebivelId: UUID,
    val cedenteId: UUID,
    val cedenteNome: String,
    val modalidade: String,
    val valorFaceBrl: BigDecimal,
    val prazoMeses: Int,
    val versaoRecebivel: Long,
    val taxaBaseId: UUID,
    val taxaBaseMensal: BigDecimal,
    val taxaBaseVigenteEm: Instant,
    val spreadId: UUID,
    val spreadMensal: BigDecimal,
    val spreadVigenteEm: Instant,
    val valorPresenteBrl: BigDecimal,
    val desagioBrl: BigDecimal,
    val moeda: MoedaPagamento,
    val cotacaoCambialId: UUID?,
    val taxaBrlPorUnidade: BigDecimal?,
    val cotacaoVigenteEm: Instant?,
    val cotacaoValidaAte: Instant?,
    val valorPagamento: BigDecimal,
    val liquidadaEm: Instant,
)
