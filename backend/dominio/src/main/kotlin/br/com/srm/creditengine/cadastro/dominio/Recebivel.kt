package br.com.srm.creditengine.cadastro.dominio

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class Recebivel(
    val id: UUID,
    val organizacaoId: UUID,
    val cedenteId: UUID,
    val modalidade: String,
    val valorFaceBrl: BigDecimal,
    val prazoMeses: Int,
    val vencimento: LocalDate?,
    val status: StatusRecebivel = StatusRecebivel.DISPONIVEL,
    val versao: Long = 0,
) {
    init {
        validarDadosRecebivel(modalidade, valorFaceBrl, prazoMeses)
        require(versao >= 0) { "Versão deve ser não negativa" }
    }
}
