package br.com.srm.creditengine.extrato.aplicacao

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
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
