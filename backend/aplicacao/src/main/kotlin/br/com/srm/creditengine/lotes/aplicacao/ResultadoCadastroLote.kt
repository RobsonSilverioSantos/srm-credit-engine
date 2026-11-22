package br.com.srm.creditengine.lotes.aplicacao

import java.util.UUID

data class ResultadoCadastroLote(val loteId: UUID, val itens: List<ItemCadastradoLote>) {
    val cadastrados: Int get() = itens.size
}
