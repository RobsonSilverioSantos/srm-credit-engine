package br.com.srm.creditengine.cadastro.dominio

import br.com.srm.creditengine.precificacao.dominio.Modalidades
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class Cedente(
    val id: UUID,
    val organizacaoId: UUID,
    val nome: String,
    val documento: String,
    val ativo: Boolean = true,
) {
    init {
        require(nome.isNotBlank() && nome.length <= 160) { "Nome do cedente inválido" }
        require(documento.isNotBlank() && documento.length <= 32) { "Documento do cedente inválido" }
    }
}

enum class StatusRecebivel { DISPONIVEL, LIQUIDADO }

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

data class Recebivel(
    val id: UUID,
    val organizacaoId: UUID,
    val cedenteId: UUID,
    val modalidade: String,
    val valorFaceBrl: BigDecimal,
    val prazoMeses: Int,
    val vencimento: LocalDate?,
    val status: StatusRecebivel = StatusRecebivel.DISPONIVEL,
    val versao: Long = 0,
) {
    init {
        validarDadosRecebivel(modalidade, valorFaceBrl, prazoMeses)
        require(versao >= 0) { "Versão deve ser não negativa" }
    }
}
