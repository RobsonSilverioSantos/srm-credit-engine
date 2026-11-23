package br.com.srm.creditengine.taxas.aplicacao.porta.saida

import br.com.srm.creditengine.taxas.aplicacao.BoletimPtax
import java.time.LocalDate

fun interface ConsultarPtax {
    fun consultar(inicio: LocalDate, fim: LocalDate): List<BoletimPtax>
}
