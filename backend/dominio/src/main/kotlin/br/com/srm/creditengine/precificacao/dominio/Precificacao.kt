package br.com.srm.creditengine.precificacao.dominio

import java.math.BigDecimal
import java.math.RoundingMode

object Modalidades {
    const val DUPLICATA_MERCANTIL = "DUPLICATA_MERCANTIL"
    const val CHEQUE_PRE_DATADO = "CHEQUE_PRE_DATADO"
}

data class DadosPrecificacao(
    val modalidade: String,
    val valorFace: BigDecimal,
    val taxaBase: BigDecimal,
    val prazoMeses: Int,
    val cotacaoBrlPorUsd: BigDecimal? = null,
) {
    init {
        require(valorFace.signum() > 0) { "Valor de face deve ser positivo" }
        require(valorFace.stripTrailingZeros().scale() <= 2) {
            "Valor de face deve ser representável em centavos"
        }
        require(taxaBase.signum() >= 0) { "Taxa base deve ser não negativa" }
        require(prazoMeses > 0) { "Prazo deve ser positivo" }
        require(cotacaoBrlPorUsd == null || cotacaoBrlPorUsd.signum() > 0) {
            "Cotação BRL/USD deve ser positiva"
        }
    }
}

data class ResultadoPrecificacao(
    val valorPresenteBrl: BigDecimal,
    val desagioBrl: BigDecimal,
    val valorPresenteUsd: BigDecimal?,
)

interface EstrategiaPrecificacao {
    val modalidade: String

    fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal
}

object CalculoValorPresente {
    fun calcular(dados: DadosPrecificacao, spread: BigDecimal): BigDecimal {
        require(spread.signum() >= 0) { "Spread deve ser não negativo" }
        val fator = BigDecimal.ONE.add(dados.taxaBase).add(spread).pow(dados.prazoMeses)
        return dados.valorFace.divide(fator, 2, RoundingMode.HALF_EVEN)
    }
}

class EstrategiaComSpreadVersionado(
    override val modalidade: String,
    private val spread: BigDecimal,
) : EstrategiaPrecificacao {
    override fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal =
        CalculoValorPresente.calcular(dados, spread)
}

object DuplicataMercantil : EstrategiaPrecificacao {
    override val modalidade = Modalidades.DUPLICATA_MERCANTIL

    override fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal =
        CalculoValorPresente.calcular(dados, BigDecimal("0.015"))
}

object ChequePreDatado : EstrategiaPrecificacao {
    override val modalidade = Modalidades.CHEQUE_PRE_DATADO

    override fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal =
        CalculoValorPresente.calcular(dados, BigDecimal("0.025"))
}

class MotorPrecificacao(
    estrategias: List<EstrategiaPrecificacao> = listOf(DuplicataMercantil, ChequePreDatado),
) {
    private val estrategiasPorModalidade = estrategias.associateBy { it.modalidade }

    init {
        require(estrategias.none { it.modalidade.isBlank() }) { "Modalidade não pode ser vazia" }
        require(estrategiasPorModalidade.size == estrategias.size) { "Modalidade com estratégias duplicadas" }
    }

    fun precificar(dados: DadosPrecificacao): ResultadoPrecificacao {
        val estrategia = requireNotNull(estrategiasPorModalidade[dados.modalidade]) {
            "Modalidade não suportada: ${dados.modalidade}"
        }
        val valorPresenteBrl = estrategia.calcularValorPresente(dados).setScale(2, RoundingMode.HALF_EVEN)
        val desagioBrl = dados.valorFace.setScale(2).subtract(valorPresenteBrl)
        val valorPresenteUsd = dados.cotacaoBrlPorUsd?.let {
            valorPresenteBrl.divide(it, 2, RoundingMode.HALF_EVEN)
        }
        return ResultadoPrecificacao(valorPresenteBrl, desagioBrl, valorPresenteUsd)
    }
}
