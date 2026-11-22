package br.com.srm.creditengine.taxas.adaptador.saida

import br.com.srm.creditengine.taxas.aplicacao.BoletimPtax
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.ConsultarPtax
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.DeserializationFeature

class PtaxODataHttp(
    private val json: ObjectMapper,
    private val base: URI = URI("https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/odata/"),
    private val timeout: Duration = Duration.ofSeconds(3),
    private val tentativas: Int = 3,
    private val atraso: Duration = Duration.ofMillis(100),
) : ConsultarPtax {
    init {
        require(timeout >= Duration.ofMillis(1) && timeout <= Duration.ofSeconds(30))
        require(tentativas in 1..5 && atraso >= Duration.ZERO && atraso <= Duration.ofSeconds(5))
        require(base.scheme in setOf("https", "http") && base.rawQuery == null && base.fragment == null)
    }

    private val cliente = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build()
    private val capacidade = Semaphore(2)
    private val logger = LoggerFactory.getLogger(PtaxODataHttp::class.java)

    override fun consultar(inicio: LocalDate, fim: LocalDate): List<BoletimPtax> {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) { "HTTP BCB proibido em transação" }
        require(!fim.isBefore(inicio) && !fim.isAfter(inicio.plusDays(7))) { "Período PTAX deve ter até sete dias" }
        if (!capacidade.tryAcquire()) indisponivel("Capacidade de sincronização PTAX esgotada")
        try {
            val resultado = mutableListOf<BoletimPtax>()
            for (pagina in 0 until 10) {
                val data = DateTimeFormatter.ofPattern("MM-dd-uuuu")
                val recurso = "CotacaoMoedaPeriodo(moeda=@moeda,dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)" +
                    "?@moeda=%27USD%27&@dataInicial=%27${inicio.format(data)}%27" +
                    "&@dataFinalCotacao=%27${fim.format(data)}%27&\$format=json" +
                    "&\$orderby=dataHoraCotacao%20asc,tipoBoletim%20asc&\$top=100&\$skip=${pagina * 100}"
                val corpo = obter(base.resolve(recurso))
                val raiz = try {
                    json.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(corpo)
                } catch (e: JacksonException) {
                    throw ConsultaCambialIndisponivel("JSON PTAX inválido", e)
                }
                val valores = raiz.get("value")
                if (!raiz.isObject || valores == null || !valores.isArray || valores.size() > 100) {
                    indisponivel("Envelope PTAX inválido")
                }
                try {
                    valores.forEach { linha ->
                        val tipo = linha.get("tipoBoletim")
                        require(tipo != null && tipo.isString) { "Tipo de boletim ausente" }
                        if (tipo.stringValue() == "Fechamento") {
                            val compra = linha.get("cotacaoCompra")
                            val venda = linha.get("cotacaoVenda")
                            val hora = linha.get("dataHoraCotacao")
                            require(compra != null && compra.isNumber && venda != null && venda.isNumber &&
                                hora != null && hora.isString) { "Campos PTAX inválidos" }
                            // OData não informa offset. É hora de Brasília, não prova de publicação.
                            val local = LocalDateTime.parse(hora.stringValue().replace(' ', 'T'))
                            require(local.toLocalDate() in inicio..fim && local.nano % 1_000 == 0) { "Data PTAX inválida" }
                            val offsets = FUSO.rules.getValidOffsets(local)
                            require(offsets.size == 1) { "Horário PTAX ambíguo ou inexistente" }
                            resultado.add(BoletimPtax(compra.decimalValue(), venda.decimalValue(),
                                local.toInstant(offsets.single()), local.toLocalDate(), hora.stringValue()))
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    throw ConsultaCambialIndisponivel("Conteúdo PTAX inválido", e)
                } catch (e: java.time.DateTimeException) {
                    throw ConsultaCambialIndisponivel("Horário PTAX inválido", e)
                }
                if (valores.size() < 100) {
                    if (resultado.isEmpty()) logger.warn("PTAX sem fechamento no período consultado")
                    return resultado
                }
            }
            indisponivel("Limite de paginação PTAX excedido")
        } finally {
            capacidade.release()
        }
    }

    private fun obter(uri: URI): String {
        for (tentativa in 1..tentativas) {
            val resposta = try {
                cliente.send(HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString())
            } catch (e: IOException) {
                if (tentativa == tentativas) throw ConsultaCambialIndisponivel("Transporte PTAX indisponível", e)
                esperar(tentativa)
                continue
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ConsultaCambialIndisponivel("Consulta PTAX interrompida", e)
            }
            val status = resposta.statusCode()
            if (status == 200) {
                if (resposta.body().length > 1_048_576) indisponivel("Resposta PTAX acima do limite")
                return resposta.body()
            }
            logger.warn("Consulta PTAX falhou status={} tentativa={}", status, tentativa)
            if (status !in setOf(408, 429) && status !in 500..599 || tentativa == tentativas) {
                indisponivel("HTTP PTAX indisponível ($status)")
            }
            esperar(tentativa)
        }
        error("Tentativas inválidas")
    }

    private fun esperar(tentativa: Int) {
        logger.warn("Retentativa limitada da integração PTAX tentativa={}", tentativa)
        try { Thread.sleep(atraso.multipliedBy(tentativa.toLong()).toMillis()) } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ConsultaCambialIndisponivel("Espera PTAX interrompida", e)
        }
    }

    private fun indisponivel(mensagem: String): Nothing =
        throw ConsultaCambialIndisponivel(mensagem, IllegalStateException(mensagem))

    companion object {
        private val FUSO = ZoneId.of("America/Sao_Paulo")
    }
}
