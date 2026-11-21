package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoRecebivel
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import java.time.LocalDate
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
class RecebiveisHttp(private val cadastros: ObjectProvider<Cadastro>) {
    @PostMapping("/recebiveis")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar recebível", requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true, content = [Content(schema = Schema(implementation = RecebivelEntrada::class))],
    ))
    @ApiResponse(responseCode = "201", description = "Recebível cadastrado",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = RecebivelResposta::class))])
    fun cadastrarRecebivel(@RequestBody corpo: JsonNode): ResponseEntity<RecebivelResposta> {
        corpo.validarCampos("cedenteId", "modalidade", "valorFaceBrl", "prazoMeses", "vencimento")
        val vencimento = corpo.get("vencimento")?.let { if (it.isNull) null else LocalDate.parse(corpo.texto("vencimento")) }
        val comando = NovoRecebivel(
            uuid(corpo.texto("cedenteId")), corpo.texto("modalidade"), corpo.valorFaceBrl(), corpo.inteiro("prazoMeses"), vencimento,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(cadastros.`object`.cadastrarRecebivel(comando).resposta())
    }

    @GetMapping("/recebiveis/{id}")
    @Operation(summary = "Consultar recebível da organização")
    fun buscarRecebivel(@PathVariable id: String): RecebivelResposta =
        cadastros.`object`.buscarRecebivel(uuid(id))?.resposta() ?: throw RecursoNaoEncontrado()

    @GetMapping("/recebiveis")
    @Operation(summary = "Listar recebíveis da organização")
    fun listarRecebiveis(): List<RecebivelResposta> = cadastros.`object`.listarRecebiveis().map { it.resposta() }
}
