package br.com.srm.creditengine.extrato.aplicacao

import java.util.UUID

fun interface LeituraExtrato {
    fun consultar(organizacaoId: UUID, filtro: FiltroExtrato): PaginaExtrato
}
