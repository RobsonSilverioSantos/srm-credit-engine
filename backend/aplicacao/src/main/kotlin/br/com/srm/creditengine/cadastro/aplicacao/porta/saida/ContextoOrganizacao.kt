package br.com.srm.creditengine.cadastro.aplicacao.porta.saida

import java.util.UUID

fun interface ContextoOrganizacao {
    fun organizacaoId(): UUID
}
