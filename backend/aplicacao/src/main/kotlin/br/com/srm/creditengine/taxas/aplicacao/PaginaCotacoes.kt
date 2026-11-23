package br.com.srm.creditengine.taxas.aplicacao

import br.com.srm.creditengine.taxas.dominio.CotacaoCambial

data class PaginaCotacoes(val itens: List<CotacaoCambial>, val pagina: Int, val tamanho: Int, val total: Long)
