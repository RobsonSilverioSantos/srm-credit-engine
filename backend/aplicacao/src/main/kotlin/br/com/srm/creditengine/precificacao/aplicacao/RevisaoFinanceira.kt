package br.com.srm.creditengine.precificacao.aplicacao

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.precificacao.dominio.ResultadoPrecificacao
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.HexFormat

object RevisaoFinanceira {
    fun calcular(
        modalidade: String, valorFaceBrl: BigDecimal, prazoMeses: Int, moeda: MoedaPagamento,
        taxas: TaxasParaSnapshot, preco: ResultadoPrecificacao,
    ): String {
        val canonico = listOf(
            modalidade, valorFaceBrl.stripTrailingZeros().toPlainString(), prazoMeses, moeda,
            taxas.taxaBase.id, taxas.spread.id, taxas.cotacao?.id,
            taxas.cambio?.politicaId, taxas.cambio?.selecaoId, taxas.cambio?.autorizacaoId, taxas.cotacaoValidaAte,
            preco.valorPresenteBrl.toPlainString(), preco.desagioBrl.toPlainString(), preco.valorPresenteUsd?.toPlainString(),
        ).joinToString("|")
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonico.toByteArray(Charsets.UTF_8)))
    }
}
