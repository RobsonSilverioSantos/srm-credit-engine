package br.com.srm.creditengine.lotes.aplicacao

import br.com.srm.creditengine.cadastro.dominio.validarDadosRecebivel
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class ItemImportacao(
    val referenciaExterna: String,
    val cedenteId: UUID,
    val modalidade: String,
    val valorFaceBrl: BigDecimal,
    val prazoMeses: Int,
    val vencimento: LocalDate?,
    val moedaPagamento: MoedaPagamento,
) {
    init {
        require(referenciaExterna.matches(Regex("[A-Za-z0-9._:/-]{1,128}"))) { "Referência externa inválida." }
        validarDadosRecebivel(modalidade, valorFaceBrl, prazoMeses)
    }
}
