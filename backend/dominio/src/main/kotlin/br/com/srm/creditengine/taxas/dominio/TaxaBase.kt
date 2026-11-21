package br.com.srm.creditengine.taxas.dominio

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class TaxaBase(val id: UUID, val taxaMensal: BigDecimal, val vigenteEm: Instant, val criadoEm: Instant) {
    init { validarDecimal(taxaMensal, 2, false) }
}
