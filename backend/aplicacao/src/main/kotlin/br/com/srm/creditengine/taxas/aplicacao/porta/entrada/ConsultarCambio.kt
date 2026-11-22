package br.com.srm.creditengine.taxas.aplicacao.porta.entrada

import br.com.srm.creditengine.taxas.dominio.CotacaoAplicavel
import java.time.Instant

fun interface ConsultarCambio {
    fun consultar(referencia: Instant): CotacaoAplicavel
}
