package br.com.srm.creditengine.cadastro.aplicacao.porta.entrada

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class NovoRecebivel(
    val cedenteId: UUID,
    val modalidade: String,
    val valorFaceBrl: BigDecimal,
    val prazoMeses: Int,
    val vencimento: LocalDate? = null,
)
