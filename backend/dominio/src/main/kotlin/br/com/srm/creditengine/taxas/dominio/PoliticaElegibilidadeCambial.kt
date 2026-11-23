package br.com.srm.creditengine.taxas.dominio

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class PoliticaElegibilidadeCambial(val id: UUID, val versao: String, val ptaxMaxIdadeSegundos: Long) {
    init { require(ptaxMaxIdadeSegundos > 0) { "Defasagem PTAX deve ser positiva" } }

    fun limitePtax(referenciaBoletim: Instant): Instant = referenciaBoletim.plusSeconds(ptaxMaxIdadeSegundos)

    fun limiteManual(autorizadaEm: Instant): Instant =
        autorizadaEm.atZone(FUSO).toLocalDate().plusDays(1).atStartOfDay(FUSO).toInstant()

    fun cadastroManualDoDia(cadastradaEm: Instant, autorizadaEm: Instant): Boolean =
        cadastradaEm <= autorizadaEm && cadastradaEm.atZone(FUSO).toLocalDate() == autorizadaEm.atZone(FUSO).toLocalDate()

    companion object {
        private val FUSO = ZoneId.of("America/Sao_Paulo")
    }
}
