package br.com.srm.creditengine.taxas.aplicacao.porta.entrada

import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.SpreadRecebivel
import br.com.srm.creditengine.taxas.dominio.TaxaBase
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot
import java.math.BigDecimal
import java.time.Instant

interface TaxasPorta {
    fun registrarTaxaBase(taxa: BigDecimal, vigenteEm: Instant): TaxaBase
    fun registrarSpread(modalidade: String, spread: BigDecimal, vigenteEm: Instant): SpreadRecebivel
    fun registrarCotacao(taxaBrlPorUsd: BigDecimal, vigenteEm: Instant): CotacaoCambial
    fun consultar(modalidade: String, exigirCotacao: Boolean): TaxasParaSnapshot
}
