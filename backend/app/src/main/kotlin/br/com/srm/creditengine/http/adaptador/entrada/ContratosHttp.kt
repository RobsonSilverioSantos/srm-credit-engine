package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoCedente
import br.com.srm.creditengine.cadastro.dominio.Cedente
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
@ApiResponses(
    ApiResponse(responseCode = "400", description = "Entrada inválida", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "401", description = "Autenticação necessária", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "404", description = "Recurso não encontrado", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "409", description = "Conflito", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "503", description = "Dados financeiros ou serviço indisponível", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
    ApiResponse(responseCode = "500", description = "Falha inesperada", content = [Content(schema = Schema(implementation = ErroHttp::class))]),
)
class ContratosHttp(private val cadastros: ObjectProvider<Cadastro>) {
    @PostMapping("/cedentes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar cedente", requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true, content = [Content(schema = Schema(implementation = CedenteEntrada::class))],
    ))
    @ApiResponse(responseCode = "201", description = "Cedente cadastrado",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = CedenteResposta::class))])
    fun cadastrarCedente(@RequestBody corpo: JsonNode): ResponseEntity<CedenteResposta> {
        corpo.validarCampos("nome", "documento", "pais", "tipoDocumento")
        require(corpo.has("pais") == corpo.has("tipoDocumento")) { "País e tipo devem ser informados juntos" }
        val cedente = cadastros.`object`.cadastrarCedente(NovoCedente(
            corpo.texto("nome"), corpo.texto("documento"),
            if (corpo.has("pais")) corpo.texto("pais") else null,
            if (corpo.has("tipoDocumento")) corpo.texto("tipoDocumento") else null,
        ))
        return ResponseEntity.status(HttpStatus.CREATED).body(cedente.resposta())
    }

    @GetMapping("/cedentes/{id}")
    @Operation(summary = "Consultar cedente da organização")
    fun buscarCedente(@PathVariable id: String): CedenteResposta =
        cadastros.`object`.buscarCedente(uuid(id))?.resposta() ?: throw RecursoNaoEncontrado()

    @GetMapping("/cedentes")
    @Operation(summary = "Listar cedentes da organização")
    fun listarCedentes(): List<CedenteResposta> = cadastros.`object`.listarCedentes().map { it.resposta() }
}
