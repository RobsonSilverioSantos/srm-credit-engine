package br.com.srm.creditengine.taxas.dominio

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CotacaoCambial(
    val id: UUID,
    val taxaBrlPorUsd: BigDecimal,
    val vigenteEm: Instant,
    val criadoEm: Instant,
) {
    init { validarDecimal(taxaBrlPorUsd, 10, true) }
}
