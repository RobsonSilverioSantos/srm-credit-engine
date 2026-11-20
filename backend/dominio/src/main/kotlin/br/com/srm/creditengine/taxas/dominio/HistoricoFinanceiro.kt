package br.com.srm.creditengine.taxas.dominio

import br.com.srm.creditengine.precificacao.dominio.Modalidades
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.UUID

private fun validarDecimal(valor: BigDecimal, inteiros: Int, positivo: Boolean) {
    require(if (positivo) valor.signum() > 0 else valor.signum() >= 0) { "Valor financeiro inválido" }
    val normalizado = valor.stripTrailingZeros()
    require(normalizado.scale() <= 10 && normalizado.precision() - normalizado.scale() <= inteiros) {
        "Valor financeiro excede a precisão permitida"
    }
}

private fun validarModalidade(modalidade: String) {
    require(modalidade == Modalidades.DUPLICATA_MERCANTIL || modalidade == Modalidades.CHEQUE_PRE_DATADO) {
        "Modalidade não suportada: $modalidade"
    }
}

data class TaxaBase(val id: UUID, val taxaMensal: BigDecimal, val vigenteEm: Instant, val criadoEm: Instant) {
    init { validarDecimal(taxaMensal, 2, false) }
}

data class SpreadRecebivel(
    val id: UUID,
    val modalidade: String,
    val spreadMensal: BigDecimal,
    val vigenteEm: Instant,
    val criadoEm: Instant,
) {
    init {
        validarModalidade(modalidade)
        validarDecimal(spreadMensal, 2, false)
    }
}

data class CotacaoCambial(
    val id: UUID,
    val taxaBrlPorUsd: BigDecimal,
    val vigenteEm: Instant,
    val criadoEm: Instant,
) {
    init { validarDecimal(taxaBrlPorUsd, 10, true) }
}

data class HistoricoElegivel(
    val taxaBase: TaxaBase?,
    val spread: SpreadRecebivel?,
    val cotacao: CotacaoCambial?,
)

data class TaxasParaSnapshot(
    val referencia: Instant,
    val taxaBase: TaxaBase,
    val spread: SpreadRecebivel,
    val cotacao: CotacaoCambial?,
    val cotacaoValidaAte: Instant?,
)

class DadosFinanceirosIndisponiveis(mensagem: String) : IllegalStateException(mensagem)

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
            val limite = it.vigenteEm.plus(validadeCambial)
            if (referencia > limite) throw DadosFinanceirosIndisponiveis("Cotação BRL/USD vencida")
            limite
        }
        return TaxasParaSnapshot(referencia, base, spread, cotacao, validaAte)
    }
}
