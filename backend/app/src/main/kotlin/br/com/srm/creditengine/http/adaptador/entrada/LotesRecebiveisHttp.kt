package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.lotes.adaptador.entrada.ArquivoLoteExcessivo
import br.com.srm.creditengine.lotes.adaptador.entrada.LeitorArquivoLote
import br.com.srm.creditengine.lotes.adaptador.entrada.interpretarLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.ConflitoImportacaoLote
import br.com.srm.creditengine.lotes.aplicacao.ErroLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.ImportacaoInvalida
import br.com.srm.creditengine.lotes.aplicacao.LinhaImportacao
import br.com.srm.creditengine.lotes.aplicacao.PreviaLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.ResultadoCadastroLote
import br.com.srm.creditengine.lotes.aplicacao.StatusLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.porta.entrada.ImportarRecebiveis
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.support.MissingServletRequestPartException
import tools.jackson.databind.JsonNode

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping(value = ["/api/v1/lotes/recebiveis", "/bff/v1/lotes/recebiveis"])
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "importacao-recebiveis")
class LotesRecebiveisHttp(
    private val lotes: ObjectProvider<ImportarRecebiveis>,
    @Value("\${srm.lotes.max-linhas:200}") private val maxLinhas: Int,
    @Value("\${srm.lotes.max-bytes:2097152}") maxBytes: Int,
) {
    private val leitor = LeitorArquivoLote(maxLinhas, maxBytes)

    @PostMapping("/validacoes", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @Operation(summary = "Pré-validar arquivo CSV UTF-8 ou XLSX padronizado sem persistência",
        description = "Parte multipart arquivo; cabeçalho com cedenteId ou cedenteDocumento (BR/CNPJ). " +
            "A confirmação usa o comando canônico com cedenteId; aviso de simulação não impede cadastro.")
    @ApiResponse(responseCode = "200", description = "Prévia, sem cadastro e sem liquidação.")
    @ApiResponse(responseCode = "400", description = "Arquivo, cabeçalho ou formato inválido.")
    @ApiResponse(responseCode = "401", description = "Autenticação necessária.")
    @ApiResponse(responseCode = "413", description = "Arquivo acima do limite.")
    fun prevalidar(@RequestPart("arquivo") arquivo: MultipartFile): LoteRecebiveisPreviaHttp {
        val linhas = leitor.ler(arquivo.originalFilename, arquivo.size, arquivo.inputStream)
        val previa = lotes.`object`.prevalidar(linhas)
        return LoteRecebiveisPreviaHttp(previa.all { it.status == StatusLinhaLote.NOVO }, previa.map { it.http() })
    }

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Confirmar importação atômica sem liquidação",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true, content = [Content(schema = Schema(implementation = LoteRecebiveisConfirmacaoHttp::class))],
        ))
    @ApiResponse(responseCode = "201", description = "Lote cadastrado; replay retorna o resultado original.")
    @ApiResponse(responseCode = "400", description = "Requisição inválida.")
    @ApiResponse(responseCode = "401", description = "Autenticação necessária.")
    @ApiResponse(responseCode = "409", description = "Duplicidade de referência, conflito ou chave reutilizada com outro comando.")
    @ApiResponse(responseCode = "422", description = "Uma ou mais linhas não podem ser cadastradas.")
    fun confirmar(
        @RequestHeader("Idempotency-Key") chave: String,
        @RequestBody corpo: JsonNode,
    ): ResultadoCadastroLote {
        corpo.validarCampos("itens")
        val itens = corpo.get("itens")
        require(itens != null && itens.isArray && itens.size() in 1..maxLinhas) { "Itens inválidos." }
        return lotes.`object`.confirmar(chave, (0 until itens.size()).map { interpretar(it + 2, itens.get(it)) })
    }

    @ExceptionHandler(ImportacaoInvalida::class)
    fun linhaInvalida(e: ImportacaoInvalida): ResponseEntity<ErroLoteRecebiveisHttp> =
        ResponseEntity.status(422).body(
            ErroLoteRecebiveisHttp("LOTE_INVALIDO", "O lote não foi cadastrado.", e.linhas.map { it.http() }),
        )

    @ExceptionHandler(ConflitoImportacaoLote::class)
    fun conflito(): ResponseEntity<ErroHttp> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErroHttp("CONFLITO", "A operação conflita com o estado atual."))

    @ExceptionHandler(ArquivoLoteExcessivo::class)
    fun excesso(): ResponseEntity<ErroHttp> =
        ResponseEntity.status(413).body(ErroHttp("ARQUIVO_EXCESSIVO", "Arquivo acima do tamanho permitido."))

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun parteAusente(): ResponseEntity<ErroHttp> =
        ResponseEntity.badRequest().body(ErroHttp("ENTRADA_INVALIDA", "A parte arquivo é obrigatória."))

    private fun interpretar(linha: Int, item: JsonNode): LinhaImportacao = try {
        item.validarCampos(*LeitorArquivoLote.CABECALHO.toTypedArray())
        val valores = LeitorArquivoLote.CABECALHO.map { campo ->
            when (campo) {
                "prazoMeses" -> item.inteiro(campo).toString()
                "vencimento" -> if (item.get(campo)?.isNull != false) "" else item.texto(campo)
                else -> item.texto(campo)
            }
        }
        interpretarLinhaLote(linha, valores)
    } catch (_: IllegalArgumentException) {
        LinhaImportacao(linha, null, listOf(ErroLinhaLote("linha", "Objeto, campos ou tipos inválidos.")))
    }

    private fun PreviaLinhaLote.http() = LoteRecebivelLinhaHttp(
        linha, status, comando?.let {
            LoteRecebivelComandoHttp(it.referenciaExterna, it.cedenteId.toString(), it.modalidade,
                it.valorFaceBrl.toPlainString(), it.prazoMeses, it.vencimento?.toString(), it.moedaPagamento.name)
        }, erros, simulacao?.resposta(), avisoSimulacao, recebivelId,
    )
}
