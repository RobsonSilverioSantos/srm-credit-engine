package br.com.srm.creditengine.taxas.dominio

import java.time.Duration
import java.time.Instant
import java.util.UUID

class SeletorTaxas(private val validadeCambial: Duration) {
    init {
        require(!validadeCambial.isNegative && !validadeCambial.isZero && validadeCambial.nano % 1_000 == 0) {
            "Validade cambial deve ser positiva e representável em microssegundos"
        }
    }

    fun selecionar(
        modalidade: String,
        referencia: Instant,
        exigirCotacao: Boolean,
        historico: HistoricoElegivel,
        cambio: CotacaoAplicavel? = null,
    ): TaxasParaSnapshot {
        validarModalidade(modalidade)
        val base = historico.taxaBase ?: throw DadosFinanceirosIndisponiveis("Taxa base indisponível")
        val spread = historico.spread ?: throw DadosFinanceirosIndisponiveis("Spread indisponível")
        fun validarInstantes(vigente: Instant, criado: Instant) {
            if (vigente > referencia || criado > referencia) {
                throw DadosFinanceirosIndisponiveis("Registro financeiro futuro")
            }
        }
        validarInstantes(base.vigenteEm, base.criadoEm)
        validarInstantes(spread.vigenteEm, spread.criadoEm)
        if (spread.modalidade != modalidade) throw DadosFinanceirosIndisponiveis("Spread de outra modalidade")
        val cotacao = if (exigirCotacao) {
            historico.cotacao ?: throw DadosFinanceirosIndisponiveis("Cotação BRL/USD indisponível")
        } else null
        val validaAte = cotacao?.let {
            validarInstantes(it.vigenteEm, it.criadoEm)
            if (cambio != null) {
                check(cambio.cotacao == it)
                cambio.validar(referencia)
                return@let cambio.validaAte
            }
            if (it.fonte != FonteCambial.DEMO) {
                throw DadosFinanceirosIndisponiveis("Fonte cambial exige política explícita")
            }
            val limite = it.vigenteEm.plus(validadeCambial)
            if (referencia > limite) throw DadosFinanceirosIndisponiveis("Cotação BRL/USD vencida")
            limite
        }
        return TaxasParaSnapshot(referencia, base, spread, cotacao, validaAte, cambio)
    }
}
