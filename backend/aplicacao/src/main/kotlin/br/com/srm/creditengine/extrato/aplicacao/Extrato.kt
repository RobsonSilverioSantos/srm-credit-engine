package br.com.srm.creditengine.extrato.aplicacao

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class FiltroExtrato(
    val inicio: LocalDate?,
    val fim: LocalDate?,
    val cedenteId: UUID?,
    val moeda: MoedaPagamento?,
    val pagina: Int,
    val tamanho: Int,
) {
    init {
        require(inicio == null || fim == null || inicio <= fim) { "Período inválido" }
        require(pagina >= 0 && tamanho in 1..100) { "Paginação inválida" }
    }
}

data class ItemExtrato(
    val liquidacaoId: UUID,
    val recebivelId: UUID,
    val cedenteId: UUID,
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

data class PaginaExtrato(val itens: List<ItemExtrato>, val pagina: Int, val tamanho: Int, val total: Long)

fun interface ConsultarExtrato {
    fun consultar(filtro: FiltroExtrato): PaginaExtrato
}

fun interface LeituraExtrato {
    fun consultar(organizacaoId: UUID, filtro: FiltroExtrato): PaginaExtrato
}

class ServicoExtrato(private val contexto: ContextoOrganizacao, private val leitura: LeituraExtrato) : ConsultarExtrato {
    override fun consultar(filtro: FiltroExtrato): PaginaExtrato = leitura.consultar(contexto.organizacaoId(), filtro)
}
