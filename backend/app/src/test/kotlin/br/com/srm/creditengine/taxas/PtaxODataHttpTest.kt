package br.com.srm.creditengine.taxas

import br.com.srm.creditengine.taxas.adaptador.saida.ConsultaCambialIndisponivel
import br.com.srm.creditengine.taxas.adaptador.saida.PtaxODataHttp
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.support.TransactionSynchronizationManager
import tools.jackson.databind.json.JsonMapper

class PtaxODataHttpTest {
    private val servidor = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { start() }
    private val chamadas = AtomicInteger()
    private val inicio = LocalDate.parse("2026-11-19")
    private val fechamento = """{"cotacaoCompra":5.88830,"cotacaoVenda":5.88890,"dataHoraCotacao":"2026-11-19 13:04:18.325373","tipoBoletim":"Fechamento"}"""

    @AfterEach
    fun encerrar() { servidor.stop(0) }

    private fun adaptador(
        timeout: Duration = Duration.ofSeconds(1),
        resposta: (Int) -> Pair<Int, String>,
    ): PtaxODataHttp {
        servidor.createContext("/") { req ->
            val n = chamadas.incrementAndGet()
            assertThat(req.requestURI.rawQuery).contains("@moeda=%27USD%27", "@dataInicial=%2711-19-2026%27",
                "\$top=100", "\$skip=")
            val (status, corpo) = resposta(n)
            val bytes = corpo.toByteArray()
            req.sendResponseHeaders(status, bytes.size.toLong())
            req.responseBody.use { it.write(bytes) }
        }
        return PtaxODataHttp(JsonMapper.builder().build(),
            URI("http://127.0.0.1:${servidor.address.port}/"), timeout, 3, Duration.ZERO)
    }

    @Test
    fun `fixture oficial preserva venda decimal e horario Brasilia sem confundir boletins`() {
        val intermediario = fechamento.replace("Fechamento", "Intermediário").replace("5.88890", "9.99999")
        val interbancario = fechamento.replace("Fechamento", "Fechamento Interbancário")
        val http = adaptador { 200 to """{"value":[$intermediario,$fechamento,$interbancario]}""" }
        val b = http.consultar(inicio, inicio.plusDays(1)).single()
        assertThat(b.venda).isEqualByComparingTo("5.88890")
        assertThat(b.compra).isEqualByComparingTo("5.88830")
        assertThat(b.referencia).isEqualTo(Instant.parse("2026-11-19T16:04:18.325373Z"))
        assertThat(b.horarioOriginal).isEqualTo("2026-11-19 13:04:18.325373")
        assertThat(chamadas.get()).isEqualTo(1)
    }

    @Test
    fun `resposta oficial coletada do OData corresponde ao fechamento publicado em horario de Brasilia`() {
        val corpo = checkNotNull(javaClass.getResourceAsStream("/ptax/bcb-usd-2026-11-19.json"))
            .bufferedReader().use { it.readText() }
        val b = adaptador { 200 to corpo }.consultar(inicio, inicio.plusDays(1)).single()
        assertThat(b.compra).isEqualByComparingTo("5.88830")
        assertThat(b.venda).isEqualByComparingTo("5.88890")
        assertThat(b.referencia).isEqualTo(Instant.parse("2026-11-19T16:04:18.325373Z"))
    }

    @Test
    fun `repete somente falhas transitorias e nao interpreta array vazio como taxa zero`() {
        val http = adaptador { if (it < 3) 503 to "{}" else 200 to """{"value":[]}""" }
        assertThat(http.consultar(inicio, inicio)).isEmpty()
        assertThat(chamadas.get()).isEqualTo(3)
    }

    @Test
    fun `falha permanente nao e repetida`() {
        val http = adaptador { 400 to "{}" }
        assertThrows<ConsultaCambialIndisponivel> { http.consultar(inicio, inicio) }
        assertThat(chamadas.get()).isEqualTo(1)
    }

    @Test
    fun `timeout e limitado sem conversao para sucesso`() {
        val http = adaptador(Duration.ofMillis(20)) {
            Thread.sleep(100)
            200 to """{"value":[]}"""
        }
        assertThrows<ConsultaCambialIndisponivel> { http.consultar(inicio, inicio) }
    }

    @Test
    fun `rejeita conteudo invalido ausente negativo precisao e compra venda inconsistente`() {
        var corpo = ""
        val http = adaptador { 200 to corpo }
        listOf(
            "{", "{}", """{"value":{}}""", """{"value":[{}]}""",
            """{"value":[${fechamento.replace("\"cotacaoVenda\":5.88890,", "")}]}""",
            """{"value":[${fechamento.replace("5.88890", "0")}]}""",
            """{"value":[${fechamento.replace("5.88890", "-1")}]}""",
            """{"value":[${fechamento.replace("5.88890", "5.12345678901")}]}""",
            """{"value":[${fechamento.replace("5.88890", "5.00000")}]}""",
            """{"value":[${fechamento.replace("2026-11-19", "2026-11-20")}]}""",
            """{"value":[${fechamento.replace("13:04:18.325373", "hora-invalida")}]}""",
        ).forEach {
            corpo = it
            assertThrows<ConsultaCambialIndisponivel> { http.consultar(inicio, inicio) }
        }
    }

    @Test
    fun `nenhuma chamada HTTP pode ocorrer em transacao`() {
        val http = adaptador { 200 to """{"value":[$fechamento]}""" }
        TransactionSynchronizationManager.setActualTransactionActive(true)
        try {
            assertThrows<IllegalStateException> { http.consultar(inicio, inicio) }
            assertThat(chamadas.get()).isZero()
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false)
        }
    }

    @Test
    fun `pagina resposta OData sem seguir nextLink nao confiavel`() {
        val ignorado = fechamento.replace("Fechamento", "Abertura")
        val http = adaptador {
            if (it == 1) 200 to """{"value":[${List(100) { ignorado }.joinToString(",")}],"@odata.nextLink":"https://nao-confiavel.invalid/"}"""
            else 200 to """{"value":[$fechamento]}"""
        }
        assertThat(http.consultar(inicio, inicio)).hasSize(1)
        assertThat(chamadas.get()).isEqualTo(2)
    }
}
