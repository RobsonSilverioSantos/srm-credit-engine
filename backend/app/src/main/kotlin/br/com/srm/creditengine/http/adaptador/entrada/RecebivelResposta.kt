package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.util.UUID

data class RecebivelResposta(
    val id: UUID, val cedenteId: UUID, val modalidade: String,
    @field:Schema(example = "100000.00") val valorFaceBrl: String,
    val prazoMeses: Int, val vencimento: LocalDate?, val status: String, val versao: Long,
)
