package br.com.srm.creditengine.lotes.aplicacao

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ItemImportacaoDocumento(
    val referenciaExterna: String,
    val modalidade: String,
    val valorFaceBrl: BigDecimal,
    val prazoMeses: Int,
    val vencimento: LocalDate?,
    val moedaPagamento: MoedaPagamento,
) {
    fun comCedente(id: UUID) = ItemImportacao(
        referenciaExterna, id, modalidade, valorFaceBrl, prazoMeses, vencimento, moedaPagamento,
    )
}
