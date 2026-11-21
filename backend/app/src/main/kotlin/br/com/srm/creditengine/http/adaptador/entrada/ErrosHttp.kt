package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.cadastro.aplicacao.CedenteNaoEncontrado
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.RecebivelNaoEncontrado
import br.com.srm.creditengine.taxas.adaptador.saida.ConsultaCambialIndisponivel
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import java.time.format.DateTimeParseException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.TransientDataAccessException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.resource.NoResourceFoundException

data class ErroHttp(val codigo: String, val mensagem: String)
class RecursoNaoEncontrado : RuntimeException()

@RestControllerAdvice
class ErrosHttp {
    private val logger = LoggerFactory.getLogger(ErrosHttp::class.java)
    @ExceptionHandler(IllegalArgumentException::class, DateTimeParseException::class,
        HttpMessageNotReadableException::class, MissingRequestHeaderException::class)
    fun entradaInvalida(): ResponseEntity<ErroHttp> =
        erro(HttpStatus.BAD_REQUEST, "ENTRADA_INVALIDA", "Requisição inválida.")

    @ExceptionHandler(RecursoNaoEncontrado::class, CedenteNaoEncontrado::class, RecebivelNaoEncontrado::class,
        NoResourceFoundException::class)
    fun naoEncontrado(): ResponseEntity<ErroHttp> =
        erro(HttpStatus.NOT_FOUND, "RECURSO_NAO_ENCONTRADO", "Recurso não encontrado.")

    @ExceptionHandler(ConflitoLiquidacao::class, DataIntegrityViolationException::class)
    fun conflito(): ResponseEntity<ErroHttp> =
        erro(HttpStatus.CONFLICT, "CONFLITO", "A operação conflita com o estado atual.")

    @ExceptionHandler(DadosFinanceirosIndisponiveis::class, ConsultaCambialIndisponivel::class,
        TransientDataAccessException::class)
    fun indisponivel(ex: Exception): ResponseEntity<ErroHttp> {
        logger.atWarn()
            .addKeyValue("event", "http.request.failed")
            .addKeyValue("errorType", ex.javaClass.simpleName)
            .log("Temporary request failure")
        return erro(HttpStatus.SERVICE_UNAVAILABLE, "SERVICO_INDISPONIVEL", "Dados financeiros ou serviço indisponível.")
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun status(ex: ResponseStatusException): ResponseEntity<ErroHttp> =
        if (ex.statusCode.value() == 401) {
            erro(HttpStatus.UNAUTHORIZED, "NAO_AUTENTICADO", "Autenticação necessária.")
        } else {
            ResponseEntity.status(ex.statusCode).body(ErroHttp("ERRO_HTTP", "Não foi possível processar a requisição."))
        }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun metodoNaoPermitido(): ResponseEntity<ErroHttp> =
        erro(HttpStatus.METHOD_NOT_ALLOWED, "METODO_NAO_PERMITIDO", "Método não permitido.")

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun midiaNaoSuportada(): ResponseEntity<ErroHttp> =
        erro(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MIDIA_NAO_SUPORTADA", "Tipo de conteúdo não suportado.")

    @ExceptionHandler(Exception::class)
    fun inesperado(ex: Exception): ResponseEntity<ErroHttp> {
        logger.atError()
            .addKeyValue("event", "http.request.failed")
            .addKeyValue("errorType", ex.javaClass.simpleName)
            .log("Unexpected request failure")
        return erro(HttpStatus.INTERNAL_SERVER_ERROR, "ERRO_INTERNO", "Não foi possível processar a requisição.")
    }

    private fun erro(status: HttpStatus, codigo: String, mensagem: String) =
        ResponseEntity.status(status).body(ErroHttp(codigo, mensagem))
}
