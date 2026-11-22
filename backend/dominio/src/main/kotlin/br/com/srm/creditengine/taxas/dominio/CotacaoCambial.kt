package br.com.srm.creditengine.taxas.dominio

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CotacaoCambial(
    val id: UUID,
    val taxaBrlPorUsd: BigDecimal,
    val vigenteEm: Instant,
    val criadoEm: Instant,
    val fonte: FonteCambial = FonteCambial.DEMO,
    val versao: Int = 1,
    val dataBoletim: LocalDate? = null,
    val horarioOriginal: String? = null,
    val autorId: UUID? = null,
    val justificativa: String? = null,
) {
    init { validarDecimal(taxaBrlPorUsd, 10, true) }
}
