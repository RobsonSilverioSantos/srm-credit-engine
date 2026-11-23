package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.cadastro.dominio.Cedente
import br.com.srm.creditengine.cadastro.dominio.StatusDocumento
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.liquidacao.dominio.ResultadoLiquidacao
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.ResultadoSimulacao
import java.math.BigDecimal
import java.util.UUID
import tools.jackson.databind.JsonNode

internal fun Cedente.resposta() = CedenteResposta(
    id, nome, documento, ativo, identificacao?.pais, identificacao?.tipoDocumento,
    identificacao?.numero, (identificacao?.status ?: StatusDocumento.DESCONHECIDO).name,
)

internal fun Recebivel.resposta() = RecebivelResposta(
    id, cedenteId, modalidade, valorFaceBrl.toPlainString(), prazoMeses, vencimento, status.name, versao,
)

internal fun ResultadoLiquidacao.resposta() = LiquidacaoResposta(
    liquidacaoId, recebivelId, moeda.name, valorPresenteBrl.toPlainString(), desagioBrl.toPlainString(),
    valorPagamento.toPlainString(), liquidadaEm.toString(),
)

internal fun ResultadoSimulacao.resposta() = SimulacaoResposta(
    moeda.name, precificacao.valorPresenteBrl.toPlainString(), precificacao.desagioBrl.toPlainString(),
    (if (moeda == MoedaPagamento.USD) checkNotNull(precificacao.valorPresenteUsd) else precificacao.valorPresenteBrl).toPlainString(),
    taxas.taxaBase.taxaMensal.toPlainString(), taxas.spread.spreadMensal.toPlainString(),
    taxas.cotacao?.taxaBrlPorUsd?.toPlainString(), taxas.referencia.toString(), taxas.cotacaoValidaAte?.toString(),
    taxas.cotacao?.fonte?.name, revisaoCambial,
)

internal fun JsonNode.validarCampos(vararg permitidos: String) {
    require(isObject && propertyNames().all { it in permitidos }) { "Objeto ou campos inválidos" }
}

internal fun JsonNode.texto(nome: String): String {
    val campo = get(nome)
    require(campo != null && campo.isString) { "Campo inválido" }
    return campo.stringValue()
}

internal fun JsonNode.inteiro(nome: String): Int {
    val campo = get(nome)
    require(campo != null && campo.isIntegralNumber && campo.canConvertToInt()) { "Campo inválido" }
    return campo.intValue()
}

internal fun JsonNode.valorFaceBrl(): BigDecimal {
    val valor = texto("valorFaceBrl")
    require(valor.matches(Regex("[0-9]{1,17}(\\.[0-9]{1,2})?"))) { "Decimal inválido" }
    return BigDecimal(valor)
}

internal fun uuid(valor: String): UUID {
    require(valor.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
        "UUID inválido"
    }
    return UUID.fromString(valor)
}
