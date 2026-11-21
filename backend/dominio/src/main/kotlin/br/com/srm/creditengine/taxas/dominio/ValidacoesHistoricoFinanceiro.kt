package br.com.srm.creditengine.taxas.dominio

import br.com.srm.creditengine.precificacao.dominio.Modalidades
import java.math.BigDecimal

internal fun validarDecimal(valor: BigDecimal, inteiros: Int, positivo: Boolean) {
    require(if (positivo) valor.signum() > 0 else valor.signum() >= 0) { "Valor financeiro inválido" }
    val normalizado = valor.stripTrailingZeros()
    require(normalizado.scale() <= 10 && normalizado.precision() - normalizado.scale() <= inteiros) {
        "Valor financeiro excede a precisão permitida"
    }
}

internal fun validarModalidade(modalidade: String) {
    require(modalidade == Modalidades.DUPLICATA_MERCANTIL || modalidade == Modalidades.CHEQUE_PRE_DATADO) {
        "Modalidade não suportada: $modalidade"
    }
}
