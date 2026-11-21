package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.ComandoSimulacao
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.SimularRecebivel
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
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
class SimulacaoHttp(private val simulacoes: ObjectProvider<SimularRecebivel>) {
    @PostMapping("/simulacoes")
    @Operation(summary = "Simular recebível antes do cadastro",
        description = "Preço indicativo, sem persistência ou reserva de taxa e câmbio.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true, content = [Content(schema = Schema(implementation = SimulacaoEntrada::class))],
        ))
    @ApiResponse(responseCode = "200", description = "Simulação indicativa",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = SimulacaoResposta::class))])
    fun simular(@RequestBody corpo: JsonNode): SimulacaoResposta {
        corpo.validarCampos("modalidade", "valorFaceBrl", "prazoMeses", "moeda")
        val comando = ComandoSimulacao(
            corpo.texto("modalidade"), corpo.valorFaceBrl(), corpo.inteiro("prazoMeses"),
            MoedaPagamento.valueOf(corpo.texto("moeda")),
        )
        return simulacoes.`object`.simular(comando).resposta()
    }
}
