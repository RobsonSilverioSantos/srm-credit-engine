package br.com.srm.creditengine.precificacao.aplicacao

import br.com.srm.creditengine.precificacao.dominio.DadosPrecificacao
import br.com.srm.creditengine.precificacao.dominio.EstrategiaComSpreadVersionado
import br.com.srm.creditengine.precificacao.dominio.MotorPrecificacao
import br.com.srm.creditengine.precificacao.dominio.ResultadoPrecificacao
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot
import java.math.BigDecimal

fun precificarComTaxas(
    modalidade: String,
    valorFaceBrl: BigDecimal,
    prazoMeses: Int,
    taxas: TaxasParaSnapshot,
): ResultadoPrecificacao =
    MotorPrecificacao(listOf(EstrategiaComSpreadVersionado(modalidade, taxas.spread.spreadMensal))).precificar(
        DadosPrecificacao(modalidade, valorFaceBrl, taxas.taxaBase.taxaMensal, prazoMeses, taxas.cotacao?.taxaBrlPorUsd),
    )
