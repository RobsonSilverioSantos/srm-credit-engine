package br.com.srm.creditengine.lotes.aplicacao.porta.entrada

import br.com.srm.creditengine.lotes.aplicacao.ItemLiquidacaoLote
import br.com.srm.creditengine.lotes.aplicacao.PreviaLiquidacaoLote
import br.com.srm.creditengine.lotes.aplicacao.ResultadoLiquidacaoLote
import java.util.UUID

interface LiquidarLote {
    fun previa(loteId: UUID, itens: List<ItemLiquidacaoLote>): PreviaLiquidacaoLote
    fun confirmar(
        loteId: UUID, itens: List<ItemLiquidacaoLote>, hashPrevia: String,
        confirmacaoFinanceira: Boolean, chave: String,
    ): ResultadoLiquidacaoLote
}
