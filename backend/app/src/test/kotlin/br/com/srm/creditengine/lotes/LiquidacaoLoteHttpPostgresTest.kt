package br.com.srm.creditengine.lotes

import br.com.srm.creditengine.TestJwtSecret
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.lotes.adaptador.saida.LotesJdbc
import br.com.srm.creditengine.lotes.aplicacao.ItemImportacao
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LiquidacaoLoteHttpPostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var encoder: PasswordEncoder
    @Autowired private lateinit var gerenciador: PlatformTransactionManager
    @LocalServerPort private var porta: Int = 0
    private val http = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private lateinit var org: UUID
    private lateinit var usuario: UUID
    private lateinit var lote: UUID
    private lateinit var itens: List<Map<String, String>>
    private lateinit var token: String

    @BeforeEach
    fun preparar() {
        org = UUID.randomUUID()
        usuario = UUID.randomUUID()
        val cedente = UUID.randomUUID()
        val email = "$usuario@example.invalid"
        val senha = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'B17 HTTP')", org)
        jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash) VALUES (?, ?, ?, ?)",
            usuario, org, email, encoder.encode(senha))
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'B17', 'teste')", cedente, org)
        val relogio = Clock.systemUTC()
        val taxas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), relogio)
        val referencia = relogio.instant().truncatedTo(ChronoUnit.MICROS)
        taxas.registrarTaxaBase(BigDecimal("0.01"), referencia)
        taxas.registrarSpread("DUPLICATA_MERCANTIL", BigDecimal("0.015"), referencia)
        taxas.registrarCotacao(BigDecimal("5.4321"), referencia)
        val cadastro = LotesJdbc(jdbc, TransactionTemplate(gerenciador)).confirmar(
            IdentidadeUsuario(usuario, org), "cadastro-http", "a".repeat(64),
            (1..3).map {
                ItemImportacao("http-$it", cedente, "DUPLICATA_MERCANTIL", BigDecimal("100000.00"), 3,
                    null, MoedaPagamento.USD)
            },
        ) {}
        lote = cadastro.loteId
        itens = cadastro.itens.map { mapOf("recebivelId" to it.recebivelId.toString(), "moedaPagamento" to "USD") }
        val login = post("/api/v1/auth/token", json.writeValueAsString(mapOf("email" to email, "password" to senha)), auth = false)
        assertThat(login.statusCode()).isEqualTo(200)
        token = json.readTree(login.body())["accessToken"].asText()
    }

    @Test
    fun `API e BFF compartilham previa completa confirmacao explicita e replay financeiro textual`() {
        val previa = previa()
        assertThat(previa["aptoParaLiquidar"].asBoolean()).isTrue()
        assertThat(previa["itens"].size()).isEqualTo(3)
        assertThat(previa["itens"][0]["valorPagamento"].isTextual).isTrue()
        assertThat(previa["itens"][0]["valorPagamento"].asText()).isEqualTo("17094.67")
        assertThat(previa["itens"][0]["fonteCambio"].asText()).isEqualTo("DEMO")
        assertThat(previa["totaisPorMoeda"]["USD"].asText()).isEqualTo("51284.01")
        semEscritas()
        val corpo = confirmarCorpo(previa)
        assertThat(post("/api/v1/lotes/$lote/liquidacoes", corpo.replace("true", "false"), "financeira").statusCode()).isEqualTo(400)
        assertThat(post("/api/v1/lotes/$lote/liquidacoes", corpo).statusCode()).isEqualTo(400)
        semEscritas()
        val confirmado = post("/api/v1/lotes/$lote/liquidacoes", corpo, "financeira")
        assertThat(confirmado.statusCode()).isEqualTo(201)
        val replay = post("/bff/v1/lotes/$lote/liquidacoes", corpo, "financeira")
        assertThat(replay.statusCode()).isEqualTo(201)
        assertThat(replay.body()).isEqualTo(confirmado.body())
        assertThat(json.readTree(confirmado.body())["itens"].size()).isEqualTo(3)
        val divergente = corpo.replace(previa["hashPrevia"].asText(), "f".repeat(64))
        assertThat(post("/bff/v1/lotes/$lote/liquidacoes", divergente, "financeira").statusCode()).isEqualTo(409)
        assertThat(post("/bff/v1/lotes/$lote/liquidacoes", corpo, "outra").statusCode()).isEqualTo(409)
        for (tabela in listOf("liquidacoes", "auditoria_liquidacoes")) {
            assertThat(contar(tabela)).isEqualTo(3)
        }
        assertThat(contar("idempotencia")).isEqualTo(1)
        val extrato = get("/bff/v1/extrato?pagina=0&tamanho=100")
        assertThat(extrato.statusCode()).isEqualTo(200)
        assertThat(json.readTree(extrato.body())["total"].asInt()).isEqualTo(3)
    }

    @Test
    fun `contratos rejeitam payload incompleto identidade injetada lote alheio e usuario inativo`() {
        val previa = previa()
        val corpo = confirmarCorpo(previa)
        assertThat(post("/api/v1/lotes/$lote/previas-liquidacao", """{"itens":[]}""").statusCode()).isEqualTo(400)
        assertThat(post("/api/v1/lotes/$lote/previas-liquidacao",
            json.writeValueAsString(mapOf("itens" to itens.take(2)))).statusCode()).isEqualTo(400)
        assertThat(post("/api/v1/lotes/$lote/previas-liquidacao",
            json.writeValueAsString(mapOf("itens" to itens, "organizacaoId" to UUID.randomUUID()))).statusCode()).isEqualTo(400)
        assertThat(post("/api/v1/lotes/${UUID.randomUUID()}/previas-liquidacao",
            json.writeValueAsString(mapOf("itens" to itens))).statusCode()).isEqualTo(404)
        assertThat(post("/api/v1/lotes/$lote/liquidacoes", corpo, "sem-token", auth = false).statusCode()).isEqualTo(401)
        val outraOrg = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'Outro escopo B17')", outraOrg)
        val alheio = UUID.randomUUID()
        jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash) VALUES (?, ?, ?, 'hash')",
            alheio, outraOrg, "$alheio@example.invalid")
        val outroLote = UUID.randomUUID()
        val cedenteAlheio = UUID.randomUUID()
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'Alheio', 'teste')",
            cedenteAlheio, outraOrg)
        val loteAlheio = LotesJdbc(jdbc, TransactionTemplate(gerenciador)).confirmar(
            IdentidadeUsuario(alheio, outraOrg), outroLote.toString(), "b".repeat(64),
            listOf(ItemImportacao("alheio", cedenteAlheio, "DUPLICATA_MERCANTIL",
                BigDecimal("100000.00"), 3, null, MoedaPagamento.USD)),
        ) {}
        val itensAlheios = loteAlheio.itens.map {
            mapOf("recebivelId" to it.recebivelId.toString(), "moedaPagamento" to "USD")
        }
        assertThat(post("/bff/v1/lotes/${loteAlheio.loteId}/previas-liquidacao",
            json.writeValueAsString(mapOf("itens" to itensAlheios))).statusCode()).isEqualTo(404)
        assertThat(post("/bff/v1/lotes/${loteAlheio.loteId}/liquidacoes",
            json.writeValueAsString(mapOf("itens" to itensAlheios, "hashPrevia" to previa["hashPrevia"].asText(),
                "confirmacaoFinanceira" to true)), "alheio").statusCode()).isEqualTo(404)
        jdbc.update("UPDATE usuarios SET ativo = false WHERE id = ?", usuario)
        assertThat(post("/bff/v1/lotes/$lote/liquidacoes", corpo, "inativo").statusCode()).isEqualTo(401)
        semEscritas()
    }

    @Test
    fun `mudanca cambial entre previa e confirmacao retorna 409 sem substituir preco`() {
        val previa = previa()
        val taxas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), Clock.systemUTC())
        taxas.registrarCotacao(BigDecimal("6.00"), Clock.systemUTC().instant().truncatedTo(ChronoUnit.MICROS))
        val resposta = post("/bff/v1/lotes/$lote/liquidacoes", confirmarCorpo(previa), "cambio-alterado")
        assertThat(resposta.statusCode()).isEqualTo(409)
        semEscritas()
    }

    @Test
    fun `timeout na consulta financeira retorna 503 sem efeitos e retry da mesma chave recupera resultado`() {
        val previa = previa()
        val corpo = confirmarCorpo(previa)
        val travou = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val bloqueio = executor.submit {
                TransactionTemplate(gerenciador).execute {
                    jdbc.execute("LOCK TABLE cotacoes_cambiais IN ACCESS EXCLUSIVE MODE")
                    travou.countDown()
                    check(liberar.await(15, TimeUnit.SECONDS))
                }
            }
            check(travou.await(10, TimeUnit.SECONDS))
            val resposta = post("/bff/v1/lotes/$lote/liquidacoes", corpo, "timeout-http")
            assertThat(resposta.statusCode()).isEqualTo(503)
            assertThat(json.readTree(resposta.body())["codigo"].asText()).isEqualTo("SERVICO_INDISPONIVEL")
            liberar.countDown()
            bloqueio.get(10, TimeUnit.SECONDS)
            semEscritas()
            val confirmado = post("/bff/v1/lotes/$lote/liquidacoes", corpo, "timeout-http")
            assertThat(confirmado.statusCode()).isEqualTo(201)
            val replay = post("/api/v1/lotes/$lote/liquidacoes", corpo, "timeout-http")
            assertThat(replay.statusCode()).isEqualTo(201)
            assertThat(replay.body()).isEqualTo(confirmado.body())
            assertThat(contar("liquidacoes")).isEqualTo(3)
            assertThat(contar("auditoria_liquidacoes")).isEqualTo(3)
            assertThat(contar("idempotencia")).isEqualTo(1)
        } finally {
            liberar.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun previa(): JsonNode {
        val resposta = post("/api/v1/lotes/$lote/previas-liquidacao", json.writeValueAsString(mapOf("itens" to itens)))
        assertThat(resposta.statusCode()).isEqualTo(200)
        return json.readTree(resposta.body())
    }

    private fun confirmarCorpo(previa: JsonNode) = json.writeValueAsString(mapOf(
        "itens" to itens, "hashPrevia" to previa["hashPrevia"].asText(), "confirmacaoFinanceira" to true,
    ))

    private fun post(rota: String, corpo: String, chave: String? = null, auth: Boolean = true): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$porta$rota"))
            .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(corpo))
        if (auth) builder.header("Authorization", "Bearer $token")
        chave?.let { builder.header("Idempotency-Key", it) }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun get(rota: String) = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:$porta$rota"))
        .header("Authorization", "Bearer $token").GET().build(), HttpResponse.BodyHandlers.ofString())

    private fun contar(tabela: String) =
        jdbc.queryForObject("SELECT count(*) FROM $tabela WHERE organizacao_id = ?", Int::class.java, org)!!

    private fun semEscritas() {
        assertThat(contar("liquidacoes")).isZero()
        assertThat(contar("auditoria_liquidacoes")).isZero()
        assertThat(contar("idempotencia")).isZero()
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM recebiveis WHERE organizacao_id = ? AND status = 'DISPONIVEL' AND versao = 0",
            Int::class.java, org)).isEqualTo(3)
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwt(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { TestJwtSecret.value }
            registry.add("spring.datasource.hikari.connection-init-sql") { "SET statement_timeout = '2s'" }
        }
    }
}
