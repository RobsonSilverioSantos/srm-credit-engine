package br.com.srm.creditengine.liquidacao.dominio

import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.precificacao.dominio.ResultadoPrecificacao
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class MoedaPagamento { BRL, USD }

data class Liquidacao(
    val id: UUID,
    val recebivel: Recebivel,
    val taxas: TaxasParaSnapshot,
    val precificacao: ResultadoPrecificacao,
    val moeda: MoedaPagamento,
    val valorPagamento: BigDecimal,
)

data class ResultadoLiquidacao(
    val liquidacaoId: UUID,
    val recebivelId: UUID,
    val moeda: MoedaPagamento,
    val valorPresenteBrl: BigDecimal,
    val desagioBrl: BigDecimal,
    val valorPagamento: BigDecimal,
    val liquidadaEm: Instant,
    val statusHttp: Int = 201,
)

class ConflitoLiquidacao(mensagem: String) : IllegalStateException(mensagem)
class RecebivelNaoEncontrado : IllegalArgumentException("Recebível não encontrado na organização")
