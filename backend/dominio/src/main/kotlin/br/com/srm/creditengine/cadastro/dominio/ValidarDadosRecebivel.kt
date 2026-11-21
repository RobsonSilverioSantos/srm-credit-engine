package br.com.srm.creditengine.cadastro.dominio

import br.com.srm.creditengine.precificacao.dominio.Modalidades
import java.math.BigDecimal

fun validarDadosRecebivel(modalidade: String, valorFaceBrl: BigDecimal, prazoMeses: Int) {
    require(modalidade in setOf(Modalidades.DUPLICATA_MERCANTIL, Modalidades.CHEQUE_PRE_DATADO)) {
        "Modalidade não suportada"
    }
    require(valorFaceBrl.signum() > 0) { "Valor de face deve ser positivo" }
    require(valorFaceBrl.stripTrailingZeros().scale() <= 2 && valorFaceBrl < BigDecimal("100000000000000000")) {
        "Valor de face deve ser representável em NUMERIC(19,2)"
    }
    require(prazoMeses > 0) { "Prazo deve ser positivo" }
}
