package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ComandoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.LiquidarRecebivel
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
@RequestMapping(value = ["/api/v1", "/bff/v1"])
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "contratos-http")
@ApiResponses(
    ApiResponse(responseCode = "400", description = "Entrada inválida", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "401", description = "Autenticação necessária", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "404", description = "Recurso não encontrado", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "409", description = "Conflito", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "503", description = "Dados financeiros ou serviço indisponível", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "500", description = "Falha inesperada", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
)
class LiquidacaoHttp(private val liquidacoes: ObjectProvider<LiquidarRecebivel>) {
    @PostMapping("/recebiveis/{id}/liquidacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Liquidar recebível cadastrado", description = "Reutilize a chave na retentativa do mesmo comando.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true, content = [Content(schema = Schema(implementation = LiquidacaoEntrada::class))],
        ))
    @ApiResponse(responseCode = "201", description = "Liquidação confirmada ou replay",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = LiquidacaoResposta::class))])
    fun liquidar(
        @PathVariable id: String,
        @Parameter(description = "Chave idempotente por organização e operação", required = true)
        @RequestHeader("Idempotency-Key") chave: String,
        @RequestBody corpo: JsonNode,
    ): ResponseEntity<LiquidacaoResposta> {
        corpo.validarCampos("moeda", "revisaoCambial")
        val revisao = corpo.get("revisaoCambial")?.takeUnless { it.isNull }?.let {
            require(it.isString) { "Revisão inválida" }
            it.stringValue()
        }
        val comando = ComandoLiquidacao(uuid(id), MoedaPagamento.valueOf(corpo.texto("moeda")), chave, revisao)
        val resultado = liquidacoes.`object`.liquidar(comando)
        return ResponseEntity.status(resultado.metadado).body(resultado.liquidacao.resposta())
    }
}
