package br.com.srm.creditengine.taxas.aplicacao

import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoFinanceiroPorta
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import br.com.srm.creditengine.taxas.dominio.SpreadRecebivel
import br.com.srm.creditengine.taxas.dominio.TaxaBase
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ManterEConsultarTaxas(
    private val historico: HistoricoFinanceiroPorta,
    private val seletor: SeletorTaxas,
    private val relogio: Clock,
) : TaxasPorta {
    private fun agora(): Instant = relogio.instant().truncatedTo(ChronoUnit.MICROS)
    private fun validarPrecisao(instante: Instant) {
        require(instante == instante.truncatedTo(ChronoUnit.MICROS)) { "Vigência deve ter precisão de microssegundos" }
    }

    override fun registrarTaxaBase(taxa: BigDecimal, vigenteEm: Instant): TaxaBase {
        validarPrecisao(vigenteEm)
        return TaxaBase(UUID.randomUUID(), taxa, vigenteEm, agora()).also { historico.incluir(it) }
    }

    override fun registrarSpread(modalidade: String, spread: BigDecimal, vigenteEm: Instant): SpreadRecebivel {
        validarPrecisao(vigenteEm)
        return SpreadRecebivel(UUID.randomUUID(), modalidade, spread, vigenteEm, agora()).also { historico.incluir(it) }
    }

    override fun registrarCotacao(taxaBrlPorUsd: BigDecimal, vigenteEm: Instant): CotacaoCambial {
        validarPrecisao(vigenteEm)
        return CotacaoCambial(UUID.randomUUID(), taxaBrlPorUsd, vigenteEm, agora()).also { historico.incluir(it) }
    }

    override fun consultar(modalidade: String, exigirCotacao: Boolean): TaxasParaSnapshot =
        consultarEm(modalidade, exigirCotacao, agora())

    override fun consultarEm(modalidade: String, exigirCotacao: Boolean, referencia: Instant): TaxasParaSnapshot {
        validarPrecisao(referencia)
        return seletor.selecionar(modalidade, referencia, exigirCotacao, historico.consultar(modalidade, referencia, exigirCotacao))
    }
}
