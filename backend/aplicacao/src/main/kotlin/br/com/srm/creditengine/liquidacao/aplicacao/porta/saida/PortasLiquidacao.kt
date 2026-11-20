package br.com.srm.creditengine.liquidacao.aplicacao.porta.saida

import br.com.srm.creditengine.liquidacao.dominio.Liquidacao
import br.com.srm.creditengine.liquidacao.dominio.ResultadoLiquidacao
import java.util.UUID

data class IdentidadeLiquidacao(val organizacaoId: UUID, val usuarioId: UUID)

fun interface IdentidadeConfiavel {
    fun identidade(): IdentidadeLiquidacao
}

interface IdempotenciaLiquidacao {
    fun consultar(organizacaoId: UUID, chave: String, hash: ByteArray): ResultadoLiquidacao?
}

fun interface TransacaoLiquidacao {
    fun confirmar(
        identidade: IdentidadeLiquidacao,
        chave: String,
        hash: ByteArray,
        liquidacao: Liquidacao,
    ): ResultadoLiquidacao
}

interface PersistenciaLiquidacao {
    fun atualizarRecebivel(liquidacao: Liquidacao): Boolean
    fun inserir(liquidacao: Liquidacao)
}

fun interface AuditoriaLiquidacao {
    fun registrar(identidade: IdentidadeLiquidacao, liquidacao: Liquidacao)
}
