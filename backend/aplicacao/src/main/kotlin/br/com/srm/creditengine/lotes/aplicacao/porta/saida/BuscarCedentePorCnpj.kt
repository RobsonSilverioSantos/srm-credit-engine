package br.com.srm.creditengine.lotes.aplicacao.porta.saida

import java.util.UUID

fun interface BuscarCedentePorCnpj {
    fun buscar(organizacaoId: UUID, cnpjCanonico: String): UUID?
}
