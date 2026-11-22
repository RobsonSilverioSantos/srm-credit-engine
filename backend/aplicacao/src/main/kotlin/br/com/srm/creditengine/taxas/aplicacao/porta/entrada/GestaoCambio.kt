package br.com.srm.creditengine.taxas.aplicacao.porta.entrada

import br.com.srm.creditengine.taxas.aplicacao.PaginaCotacoes
import br.com.srm.creditengine.taxas.aplicacao.PaginaSelecoesCambiais
import br.com.srm.creditengine.taxas.aplicacao.SelecaoFonteCambial
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.FonteCambial
import java.math.BigDecimal
import java.util.UUID

interface GestaoCambio {
    fun registrarManual(taxa: BigDecimal, justificativa: String): CotacaoCambial
    fun selecionar(fonte: FonteCambial, cotacaoId: UUID?, justificativa: String): SelecaoFonteCambial
    fun historico(fonte: FonteCambial?, pagina: Int, tamanho: Int): PaginaCotacoes
    fun selecoes(pagina: Int, tamanho: Int): PaginaSelecoesCambiais
    fun sincronizar(): Int
}
