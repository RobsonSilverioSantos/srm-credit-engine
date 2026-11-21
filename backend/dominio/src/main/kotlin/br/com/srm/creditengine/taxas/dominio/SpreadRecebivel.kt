package br.com.srm.creditengine.taxas.dominio

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class SpreadRecebivel(
    val id: UUID,
    val modalidade: String,
    val spreadMensal: BigDecimal,
    val vigenteEm: Instant,
    val criadoEm: Instant,
) {
    init {
        validarModalidade(modalidade)
        validarDecimal(spreadMensal, 2, false)
    }
}
