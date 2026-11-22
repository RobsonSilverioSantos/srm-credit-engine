package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.lotes.aplicacao.ItemLiquidacaoLote
import br.com.srm.creditengine.lotes.aplicacao.porta.entrada.LiquidarLote
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping(value = ["/api/v1/lotes/{loteId}", "/bff/v1/lotes/{loteId}"])
@SecurityRequirement(name = "bearerAuth")
class LiquidacaoLoteHttp(
    private val servico: ObjectProvider<LiquidarLote>,
    @param:Value("\${srm.lotes.max-linhas:200}") private val maxTitulos: Int,
) {
    @PostMapping("/previas-liquidacao")
    @Operation(summary = "Prévia financeira integral sem persistência nem reserva de preço",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = [Content(schema = Schema(implementation = PreviaLiquidacaoLoteEntrada::class))]))
    @ApiResponse(responseCode = "200", description = "Todos os itens e impedimentos; somente 100% válidos geram hash.")
    fun previa(@PathVariable loteId: String, @RequestBody corpo: JsonNode) =
        corpo.validarCampos("itens").let { servico.`object`.previa(uuid(loteId), itens(corpo)) }

    @PostMapping("/liquidacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Confirmar explicitamente a liquidação atômica de todo o lote",
        description = "Requer hash da prévia completa, confirmacaoFinanceira=true e Idempotency-Key estável. " +
            "Valores decimais são strings. Não há sucesso parcial nem retry financeiro automático.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = [Content(schema = Schema(implementation = LiquidacaoLoteEntrada::class))]))
    @ApiResponse(responseCode = "201", description = "Todos os títulos confirmados ou replay original.")
    @ApiResponse(responseCode = "400", description = "Entrada incompleta, quantidade ou confirmação inválida.")
    @ApiResponse(responseCode = "401", description = "Identidade autenticada obrigatória.")
    @ApiResponse(responseCode = "404", description = "Lote não encontrado no escopo autenticado.")
    @ApiResponse(responseCode = "409", description = "Prévia obsoleta, conflito de estado/versão ou chave divergente; rollback integral.")
    @ApiResponse(responseCode = "503", description = "Dados financeiros indisponíveis; nenhuma confirmação.")
    fun confirmar(
        @PathVariable loteId: String,
        @RequestHeader("Idempotency-Key") chave: String,
        @RequestBody corpo: JsonNode,
    ): br.com.srm.creditengine.lotes.aplicacao.ResultadoLiquidacaoLote {
        corpo.validarCampos("itens", "hashPrevia", "confirmacaoFinanceira")
        val confirmacao = corpo.get("confirmacaoFinanceira")
        require(confirmacao != null && confirmacao.isBoolean) { "Confirmação inválida." }
        return servico.`object`.confirmar(uuid(loteId), itens(corpo), corpo.texto("hashPrevia"), confirmacao.asBoolean(), chave)
    }

    private fun itens(corpo: JsonNode): List<ItemLiquidacaoLote> {
        val itens = corpo.get("itens")
        require(itens != null && itens.isArray && itens.size() in 1..maxTitulos) { "Itens inválidos." }
        return (0 until itens.size()).map {
            val item = itens.get(it)
            item.validarCampos("recebivelId", "moedaPagamento")
            ItemLiquidacaoLote(uuid(item.texto("recebivelId")), MoedaPagamento.valueOf(item.texto("moedaPagamento")))
        }
    }
}
