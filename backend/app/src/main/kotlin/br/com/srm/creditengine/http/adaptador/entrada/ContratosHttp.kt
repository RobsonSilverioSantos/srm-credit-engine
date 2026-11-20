package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoCedente
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoRecebivel
import br.com.srm.creditengine.cadastro.dominio.Cedente
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ComandoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.LiquidarRecebivel
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.liquidacao.dominio.RecebivelNaoEncontrado
import br.com.srm.creditengine.liquidacao.dominio.ResultadoLiquidacao
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

data class CedenteEntrada(val nome: String, val documento: String)
data class RecebivelEntrada(
    val cedenteId: UUID,
    @field:Schema(allowableValues = ["DUPLICATA_MERCANTIL", "CHEQUE_PRE_DATADO"])
    val modalidade: String,
    @field:Schema(example = "100000.00", description = "BRL em string decimal") val valorFaceBrl: String,
    val prazoMeses: Int,
    val vencimento: LocalDate? = null,
)
data class LiquidacaoEntrada(@field:Schema(allowableValues = ["BRL", "USD"]) val moeda: String)
data class CedenteResposta(val id: UUID, val nome: String, val documento: String, val ativo: Boolean)
data class RecebivelResposta(
    val id: UUID, val cedenteId: UUID, val modalidade: String,
    @field:Schema(example = "100000.00") val valorFaceBrl: String,
    val prazoMeses: Int, val vencimento: LocalDate?, val status: String, val versao: Long,
)
data class LiquidacaoResposta(
    val liquidacaoId: UUID, val recebivelId: UUID, val moeda: String,
    @field:Schema(example = "92859.94") val valorPresenteBrl: String,
    @field:Schema(example = "7140.06") val desagioBrl: String,
    @field:Schema(example = "17094.67") val valorPagamento: String,
    @field:Schema(example = "2026-11-20T12:00:00Z") val liquidadaEm: String,
)

private fun Cedente.resposta() = CedenteResposta(id, nome, documento, ativo)
private fun Recebivel.resposta() = RecebivelResposta(
    id, cedenteId, modalidade, valorFaceBrl.toPlainString(), prazoMeses, vencimento, status.name, versao,
)
private fun ResultadoLiquidacao.resposta() = LiquidacaoResposta(
    liquidacaoId, recebivelId, moeda.name, valorPresenteBrl.toPlainString(), desagioBrl.toPlainString(),
    valorPagamento.toPlainString(), liquidadaEm.toString(),
)

private fun JsonNode.validarCampos(vararg permitidos: String) {
    require(isObject && propertyNames().all { it in permitidos }) { "Objeto ou campos inválidos" }
}
private fun JsonNode.texto(nome: String): String {
    val campo = get(nome)
    require(campo != null && campo.isString) { "Campo inválido" }
    return campo.stringValue()
}
private fun JsonNode.inteiro(nome: String): Int {
    val campo = get(nome)
    require(campo != null && campo.isIntegralNumber && campo.canConvertToInt()) { "Campo inválido" }
    return campo.intValue()
}
private fun uuid(valor: String): UUID {
    require(valor.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
        "UUID inválido"
    }
    return UUID.fromString(valor)
}

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
class ContratosHttp(
    private val cadastros: ObjectProvider<Cadastro>,
    private val liquidacoes: ObjectProvider<LiquidarRecebivel>,
) {
    @PostMapping("/cedentes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar cedente", requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true, content = [Content(schema = Schema(implementation = CedenteEntrada::class))],
    ))
    @ApiResponse(responseCode = "201", description = "Cedente cadastrado",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = CedenteResposta::class))])
    fun cadastrarCedente(@RequestBody corpo: JsonNode): ResponseEntity<CedenteResposta> {
        corpo.validarCampos("nome", "documento")
        val cedente = cadastros.`object`.cadastrarCedente(NovoCedente(corpo.texto("nome"), corpo.texto("documento")))
        return ResponseEntity.status(HttpStatus.CREATED).body(cedente.resposta())
    }

    @GetMapping("/cedentes/{id}")
    @Operation(summary = "Consultar cedente da organização")
    fun buscarCedente(@PathVariable id: String): CedenteResposta =
        cadastros.`object`.buscarCedente(uuid(id))?.resposta() ?: throw RecursoNaoEncontrado()

    @GetMapping("/cedentes")
    @Operation(summary = "Listar cedentes da organização")
    fun listarCedentes(): List<CedenteResposta> = cadastros.`object`.listarCedentes().map { it.resposta() }

    @PostMapping("/recebiveis")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar recebível", requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true, content = [Content(schema = Schema(implementation = RecebivelEntrada::class))],
    ))
    @ApiResponse(responseCode = "201", description = "Recebível cadastrado",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = RecebivelResposta::class))])
    fun cadastrarRecebivel(@RequestBody corpo: JsonNode): ResponseEntity<RecebivelResposta> {
        corpo.validarCampos("cedenteId", "modalidade", "valorFaceBrl", "prazoMeses", "vencimento")
        val valor = corpo.texto("valorFaceBrl")
        require(valor.matches(Regex("[0-9]{1,17}(\\.[0-9]{1,2})?"))) { "Decimal inválido" }
        val vencimento = corpo.get("vencimento")?.let { if (it.isNull) null else LocalDate.parse(corpo.texto("vencimento")) }
        val comando = NovoRecebivel(
            uuid(corpo.texto("cedenteId")), corpo.texto("modalidade"), BigDecimal(valor), corpo.inteiro("prazoMeses"), vencimento,
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
        corpo.validarCampos("moeda")
        val comando = ComandoLiquidacao(uuid(id), MoedaPagamento.valueOf(corpo.texto("moeda")), chave)
        val resultado = liquidacoes.`object`.liquidar(comando)
        return ResponseEntity.status(resultado.statusHttp).body(resultado.resposta())
    }
}
