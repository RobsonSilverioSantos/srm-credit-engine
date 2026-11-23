package br.com.srm.creditengine.liquidacao.dominio

import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.precificacao.dominio.ResultadoPrecificacao
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Liquidacao(
    val id: UUID,
    val recebivel: Recebivel,
    val taxas: TaxasParaSnapshot,
    val precificacao: ResultadoPrecificacao,
    val moeda: MoedaPagamento,
    val valorPagamento: BigDecimal,
)
