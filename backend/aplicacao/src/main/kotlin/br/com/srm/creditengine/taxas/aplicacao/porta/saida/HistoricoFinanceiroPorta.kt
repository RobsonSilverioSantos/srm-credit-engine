package br.com.srm.creditengine.taxas.aplicacao.porta.saida

import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.HistoricoElegivel
import br.com.srm.creditengine.taxas.dominio.SpreadRecebivel
import br.com.srm.creditengine.taxas.dominio.TaxaBase
import java.time.Instant

interface HistoricoFinanceiroPorta {
    fun incluir(taxa: TaxaBase)
    fun incluir(spread: SpreadRecebivel)
    fun incluir(cotacao: CotacaoCambial)
    fun consultar(modalidade: String, referencia: Instant, incluirCotacao: Boolean): HistoricoElegivel
}
