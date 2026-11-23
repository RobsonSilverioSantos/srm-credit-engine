package br.com.srm.creditengine.lotes.aplicacao

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import java.util.UUID

data class ItemLiquidacaoLote(val recebivelId: UUID, val moedaPagamento: MoedaPagamento)
