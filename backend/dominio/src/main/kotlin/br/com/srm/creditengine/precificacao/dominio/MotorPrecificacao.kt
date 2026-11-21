package br.com.srm.creditengine.precificacao.dominio

import java.math.RoundingMode

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
