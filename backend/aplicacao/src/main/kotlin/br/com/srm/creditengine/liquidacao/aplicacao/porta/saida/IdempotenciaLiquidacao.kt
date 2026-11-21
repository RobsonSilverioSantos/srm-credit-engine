package br.com.srm.creditengine.liquidacao.aplicacao.porta.saida

import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ResultadoOperacaoLiquidacao
import java.util.UUID

interface IdempotenciaLiquidacao {
    fun consultar(organizacaoId: UUID, chave: String, hash: ByteArray): ResultadoOperacaoLiquidacao?
}
