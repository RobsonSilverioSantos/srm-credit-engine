package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.taxas.aplicacao.SelecaoFonteCambial
import br.com.srm.creditengine.taxas.aplicacao.PaginaSelecoesCambiais
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.GestaoCambio
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.FonteCambial
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import java.math.BigDecimal
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = ["srm.taxas.enabled"], havingValue = "true", matchIfMissing = true)
@RequestMapping(value = ["/api/v1/cotacoes-cambiais", "/bff/v1/cotacoes-cambiais"])
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "cambio", description = "BRL por USD. PTAX fechamento VENDA padrão; nenhuma contingência automática.")
@ApiResponses(
    ApiResponse(responseCode = "400", description = "Entrada inválida"),
    ApiResponse(responseCode = "401", description = "Autenticação necessária"),
    ApiResponse(responseCode = "403", description = "Somente MASTER/ADMIN podem cadastrar, autorizar ou sincronizar"),
    ApiResponse(responseCode = "409", description = "Cotação ausente na organização, já autorizada ou de outro dia"),
    ApiResponse(responseCode = "503", description = "PTAX indisponível; fonte não é alterada automaticamente"),
)
class CambioHttp(private val cambio: GestaoCambio) {
    @PostMapping("/manuais")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar cotação MANUAL sem ativar",
        description = "Instante do servidor, sem vigência retroativa/agendada. Justificativa e autor auditados; histórico imutável.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = [Content(schema = Schema(implementation = CotacaoManualEntrada::class))]))
    @ApiResponse(responseCode = "201", description = "Cadastrada, ainda não autorizada")
    fun cadastrar(@RequestBody corpo: JsonNode): CotacaoCambialResposta {
        corpo.validarCampos("taxaBrlPorUsd", "justificativa")
        val texto = corpo.texto("taxaBrlPorUsd")
        require(texto.matches(Regex("[0-9]{1,10}(\\.[0-9]{1,10})?"))) { "Taxa decimal textual inválida" }
        return cambio.registrarManual(BigDecimal(texto), corpo.texto("justificativa")).resposta()
    }

    @PostMapping("/selecoes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Selecionar PTAX ou autorizar explicitamente MANUAL na organização",
        description = "MANUAL válida da autorização até 00:00 exclusiva do próximo dia America/Sao_Paulo; exige nova prévia. " +
            "Expiração não retorna a PTAX. Uma cotação manual não pode ser reutilizada em outra autorização.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = [Content(schema = Schema(implementation = SelecaoCambialEntrada::class))]))
    @ApiResponse(responseCode = "201", description = "Seleção/autorização imutável registrada")
    fun selecionar(@RequestBody corpo: JsonNode): SelecaoFonteCambial {
        corpo.validarCampos("fonte", "cotacaoId", "justificativa")
        val id = corpo.get("cotacaoId")?.takeUnless { it.isNull }?.let {
            require(it.isString) { "Cotação inválida" }
            uuid(it.stringValue())
        }
        return cambio.selecionar(FonteCambial.valueOf(corpo.texto("fonte")), id, corpo.texto("justificativa"))
    }

    @GetMapping
    @Operation(summary = "Consultar histórico paginado, com isolamento de cotações MANUAL",
        description = "Somente inclusão. OPERADOR não recebe autor/justificativa. Página começa em zero, tamanho máximo 100.")
    fun historico(
        @RequestParam(required = false) fonte: String?,
        @RequestParam(defaultValue = "0") pagina: String,
        @RequestParam(defaultValue = "20") tamanho: String,
    ): PaginaCotacoesResposta {
        val resultado = cambio.historico(fonte?.let(FonteCambial::valueOf), numero(pagina), numero(tamanho))
        return PaginaCotacoesResposta(resultado.itens.map { it.resposta() }, resultado.pagina, resultado.tamanho, resultado.total)
    }

    @GetMapping("/selecoes")
    @Operation(summary = "Consultar histórico paginado de seleções e autorizações da organização",
        description = "Mais recente primeiro. MANUAL expirada permanece selecionada; não há retorno automático a PTAX. " +
            "Antes da primeira seleção aplica-se PTAX, exceto modo DEMO explicitamente configurado. " +
            "OPERADOR não recebe autor/justificativa.")
    fun selecoes(
        @RequestParam(defaultValue = "0") pagina: String,
        @RequestParam(defaultValue = "20") tamanho: String,
    ): PaginaSelecoesCambiais = cambio.selecoes(numero(pagina), numero(tamanho))

    @PostMapping("/ptax/sincronizacoes")
    @Operation(summary = "Sincronizar sete dias de boletins BCB sob autorização",
        description = "Timeout, tentativas e concorrência limitados; consulta HTTP antes de transação curta. " +
            "Reimportação idêntica não cria versão nem renova idade/primeira observação.")
    @ApiResponse(responseCode = "200", description = "Sincronizada; zero inclusões também é possível")
    fun sincronizar(): SincronizacaoPtaxResposta = SincronizacaoPtaxResposta(cambio.sincronizar())

    private fun numero(texto: String): Int {
        require(texto.matches(Regex("[0-9]{1,9}"))) { "Paginação inválida" }
        return texto.toInt()
    }

    private fun CotacaoCambial.resposta() = CotacaoCambialResposta(
        id, taxaBrlPorUsd.toPlainString(), "BRL", "USD", fonte.name, "VENDA", versao,
        vigenteEm.toString(), criadoEm.toString(), dataBoletim?.toString(), horarioOriginal,
        if (fonte == FonteCambial.BCB_PTAX) "FECHAMENTO" else null,
        if (fonte == FonteCambial.BCB_PTAX) "America/Sao_Paulo" else null, autorId, justificativa,
    )
}
