package br.com.srm.creditengine.lotes.aplicacao.porta.saida

import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.Liquidacao
import br.com.srm.creditengine.lotes.aplicacao.ResultadoLiquidacaoLote
import java.util.UUID

interface PersistenciaLiquidacaoLote {
    fun recebiveis(organizacaoId: UUID, loteId: UUID): List<Recebivel>
    fun confirmar(
        identidade: IdentidadeLiquidacao, loteId: UUID, chave: String, hash: ByteArray,
        preparar: () -> List<Liquidacao>, revalidar: (List<Liquidacao>) -> Unit,
    ): ResultadoLiquidacaoLote
}
