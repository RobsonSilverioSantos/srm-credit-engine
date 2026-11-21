package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

data class RecebivelEntrada(
    val cedenteId: UUID,
    @field:Schema(allowableValues = ["DUPLICATA_MERCANTIL", "CHEQUE_PRE_DATADO"])
    val modalidade: String,
    @field:Schema(example = "100000.00", description = "BRL em string decimal") val valorFaceBrl: String,
    val prazoMeses: Int,
    val vencimento: LocalDate? = null,
)
