package br.com.srm.creditengine.extrato.adaptador.entrada

import java.util.UUID

data class ItemExtratoResposta(
    val liquidacaoId: UUID,
    val recebivelId: UUID,
    val cedenteId: UUID,
    val modalidade: String,
    val valorFaceBrl: String,
    val prazoMeses: Int,
    val versaoRecebivel: Long,
    val taxaBaseId: UUID,
    val taxaBaseMensal: String,
    val taxaBaseVigenteEm: String,
    val spreadId: UUID,
    val spreadMensal: String,
    val spreadVigenteEm: String,
    val valorPresenteBrl: String,
    val desagioBrl: String,
    val moeda: String,
    val cotacaoCambialId: UUID?,
    val taxaBrlPorUnidade: String?,
    val cotacaoVigenteEm: String?,
    val cotacaoValidaAte: String?,
    val valorPagamento: String,
    val liquidadaEm: String,
)
