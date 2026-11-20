package br.com.srm.creditengine.http

import br.com.srm.creditengine.TestJwtSecret
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.seguranca.adaptador.entrada.JwtProperties
import br.com.srm.creditengine.taxas.adaptador.saida.ConsultaCambialIndisponivel
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ContratosHttpPostgresTest.FalhasDeTeste::class)
class ContratosHttpPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    class FalhasDeTeste {
        @Bean
        fun endpointsDeFalha() = EndpointsDeFalha()
    }

    @RestController
    class EndpointsDeFalha {
        @GetMapping("/test/falhas/{tipo}")
        fun falhar(@PathVariable tipo: String): Nothing =
            when (tipo) {
                "dados" -> throw DadosFinanceirosIndisponiveis("detalhe interno")
                "inesperada" -> throw IllegalStateException("detalhe interno")
                else -> throw ConsultaCambialIndisponivel("detalhe interno", IllegalStateException("detalhe"))
            }
    }

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var encoder: PasswordEncoder
    @Autowired private lateinit var jwtEncoder: JwtEncoder
    @Autowired private lateinit var jwtProperties: JwtProperties
    @Autowired private lateinit var taxas: TaxasPorta
    @LocalServerPort private var port: Int = 0
    private val http = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private lateinit var orgA: UUID
    private lateinit var usuarioA: UUID
    private lateinit var tokenA: String
    private lateinit var tokenB: String

    @BeforeEach
    fun preparar() {
        orgA = UUID.randomUUID()
        val orgB = UUID.randomUUID()
        usuarioA = UUID.randomUUID()
        val usuarioB = UUID.randomUUID()
        val senha = UUID.randomUUID().toString()
        val emailA = "$usuarioA@example.invalid"
        val emailB = "$usuarioB@example.invalid"
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, ?), (?, ?)", orgA, "A", orgB, "B")
        val hash = encoder.encode(senha)
        jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash) VALUES (?, ?, ?, ?), (?, ?, ?, ?)",
            usuarioA, orgA, emailA, hash, usuarioB, orgB, emailB, hash)
        tokenA = login(emailA, senha)
        tokenB = login(emailB, senha)
    }

    @Test
    fun `cadastro consulta listagem e isolamento sao equivalentes na API e BFF`() {
        val cedente = criarCedente("/api/v1")
        val id = cedente["id"].asText()
        assertThat(get("/bff/v1/cedentes/$id", tokenA).body()).isEqualTo(get("/api/v1/cedentes/$id", tokenA).body())
        assertThat(get("/api/v1/cedentes", tokenA).tree().map { it["id"].asText() }).contains(id)
        assertThat(get("/bff/v1/cedentes/$id", tokenB).statusCode()).isEqualTo(404)
        assertThat(get("/api/v1/cedentes", tokenB).tree()).isEmpty()
        val recebivel = criarRecebivel("/bff/v1", id)
        val recebivelId = recebivel["id"].asText()
        assertThat(recebivel["valorFaceBrl"].isTextual).isTrue()
        assertThat(recebivel["valorFaceBrl"].asText()).isEqualTo("100000.00")
        assertThat(get("/api/v1/recebiveis/$recebivelId", tokenA).body())
            .isEqualTo(get("/bff/v1/recebiveis/$recebivelId", tokenA).body())
        assertThat(get("/api/v1/recebiveis", tokenA).tree().map { it["id"].asText() }).contains(recebivelId)
        assertThat(get("/api/v1/recebiveis/$recebivelId", tokenB).statusCode()).isEqualTo(404)
        assertThat(post("/api/v1/recebiveis", tokenB, """{"cedenteId":"$id","modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":"1.00","prazoMeses":1}""").statusCode()).isEqualTo(404)
        assertThat(post("/bff/v1/recebiveis/$recebivelId/liquidacoes", tokenB, """{"moeda":"BRL"}""", "cross").statusCode()).isEqualTo(404)
    }

    @Test
    fun `golden cases e replay preservam resposta entre API BFF e banco`() {
        val instante = Instant.now().truncatedTo(ChronoUnit.MICROS)
        taxas.registrarTaxaBase(BigDecimal("0.01"), instante)
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), instante)
        taxas.registrarSpread(Modalidades.CHEQUE_PRE_DATADO, BigDecimal("0.025"), instante)
        taxas.registrarCotacao(BigDecimal("5.4321"), instante)
        val cedente = criarCedente("/api/v1")["id"].asText()
        val c1 = criarRecebivel("/api/v1", cedente)["id"].asText()
        val c2 = criarRecebivel("/api/v1", cedente, "CHEQUE_PRE_DATADO", "25000.00", 2)["id"].asText()
        val c3 = criarRecebivel("/bff/v1", cedente)["id"].asText()
        val primeiro = post("/api/v1/recebiveis/$c1/liquidacoes", tokenA, """{"moeda":"BRL"}""", "c1")
        assertThat(primeiro.statusCode()).isEqualTo(201)
        assertThat(primeiro.tree()["valorPagamento"].asText()).isEqualTo("92859.94")
        val replay = post("/bff/v1/recebiveis/$c1/liquidacoes", tokenA, """{"moeda":"BRL"}""", "c1")
        assertThat(replay.statusCode()).isEqualTo(primeiro.statusCode())
        assertThat(replay.body()).isEqualTo(primeiro.body())
        val armazenado = jdbc.queryForMap("SELECT resposta_http, resposta_corpo::text AS corpo FROM idempotencia WHERE organizacao_id = ? AND chave = 'c1'", orgA)
        assertThat(armazenado["resposta_http"]).isEqualTo(201)
        assertThat(json.readTree(armazenado["corpo"] as String)).isEqualTo(primeiro.tree())
        assertThat(post("/api/v1/recebiveis/$c1/liquidacoes", tokenA, """{"moeda":"USD"}""", "c1").statusCode()).isEqualTo(409)
        assertThat(post("/api/v1/recebiveis/$c1/liquidacoes", tokenA, """{"moeda":"BRL"}""", "outra").statusCode()).isEqualTo(409)
        val segundo = post("/bff/v1/recebiveis/$c2/liquidacoes", tokenA, """{"moeda":"BRL"}""", "c2")
        val terceiro = post("/api/v1/recebiveis/$c3/liquidacoes", tokenA, """{"moeda":"USD"}""", "c3")
        assertThat(segundo.statusCode()).isEqualTo(201)
        assertThat(segundo.tree()["valorPagamento"].asText()).isEqualTo("23337.77")
        assertThat(terceiro.statusCode()).isEqualTo(201)
        assertThat(terceiro.tree()["valorPagamento"].isTextual).isTrue()
        assertThat(terceiro.tree()["valorPagamento"].asText()).isEqualTo("17094.67")
        assertThat(terceiro.tree()["liquidadaEm"].asText()).endsWith("Z")
        assertThat(post("/bff/v1/recebiveis/$c3/liquidacoes", tokenA, """{"moeda":"USD"}""", "c3").body())
            .isEqualTo(terceiro.body())
    }

    @Test
    fun `valida formatos campos header e identidade sem aceitar manutencao global`() {
        val cedente = criarCedente("/api/v1")["id"].asText()
        val recebivel = criarRecebivel("/api/v1", cedente)["id"].asText()
        val url = "/api/v1/recebiveis/$recebivel/liquidacoes"
        val semChave = post(url, tokenA, """{"moeda":"BRL"}""")
        assertThat(semChave.statusCode()).isEqualTo(400)
        assertThat(semChave.tree()["codigo"].asText()).isEqualTo("ENTRADA_INVALIDA")
        assertThat(post(url, tokenA, """{"moeda":"EUR"}""", "x").statusCode()).isEqualTo(400)
        assertThat(post(url, tokenA, """{"moeda":"BRL","organizacaoId":"$orgA"}""", "x").statusCode()).isEqualTo(400)
        assertThat(get("/api/v1/cedentes/123", tokenA).statusCode()).isEqualTo(400)
        listOf(
            """{"cedenteId":"$cedente","modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":100,"prazoMeses":1}""",
            """{"cedenteId":"$cedente","modalidade":"OUTRA","valorFaceBrl":"100","prazoMeses":1}""",
            """{"cedenteId":"$cedente","modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":"100","prazoMeses":0}""",
            """{"cedenteId":"$cedente","modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":"100","prazoMeses":1,"vencimento":"invalido"}""",
        ).forEach { assertThat(post("/api/v1/recebiveis", tokenA, it).statusCode()).isEqualTo(400) }
        assertThat(post("/api/v1/taxas", tokenA, """{"taxa":"0.01"}""").statusCode()).isEqualTo(404)
        val semToken = get("/api/v1/cedentes")
        assertThat(semToken.statusCode()).isEqualTo(401)
        assertThat(semToken.tree()["codigo"].asText()).isEqualTo("NAO_AUTENTICADO")
        assertThat(get("/api/v1/cedentes", "invalido").statusCode()).isEqualTo(401)
        assertThat(get("/bff/v1/cedentes", tokenExpirado()).statusCode()).isEqualTo(401)
    }

    @Test
    fun `documentacao openapi inclui os dois contratos e bearer`() {
        val resposta = get("/v3/api-docs")
        assertThat(resposta.statusCode()).isEqualTo(200)
        val doc = resposta.tree()
        assertThat(doc["paths"]["/api/v1/recebiveis/{id}/liquidacoes"]["post"]).isNotNull()
        assertThat(doc["paths"]["/bff/v1/recebiveis/{id}/liquidacoes"]["post"]).isNotNull()
        assertThat(doc["components"]["securitySchemes"]["bearerAuth"]["scheme"].asText()).isEqualTo("bearer")
        val operacao = doc["paths"]["/api/v1/recebiveis/{id}/liquidacoes"]["post"]
        assertThat(operacao["security"][0]["bearerAuth"].isArray).isTrue()
        assertThat(operacao["parameters"].find { it["name"].asText() == "Idempotency-Key" }!!["required"].asBoolean()).isTrue()
        assertThat(operacao["requestBody"]["content"]["application/json"]["schema"]["\$ref"].asText())
            .endsWith("/LiquidacaoEntrada")
        assertThat(operacao["responses"]["201"]["content"]["application/json"]["schema"]["\$ref"].asText())
            .endsWith("/LiquidacaoResposta")
        assertThat(operacao["responses"].has("200")).isFalse()
        listOf("api", "bff").forEach { prefixo ->
            listOf("cedentes" to "CedenteResposta", "recebiveis" to "RecebivelResposta").forEach { (rota, schema) ->
                assertThat(doc["paths"]["/$prefixo/v1/$rota"]["post"]["responses"]["201"]
                    ["content"]["application/json"]["schema"]["\$ref"].asText()).endsWith("/$schema")
            }
        }
        assertThat(doc["components"]["schemas"]["RecebivelEntrada"]["properties"]["valorFaceBrl"]["type"].asText())
            .isEqualTo("string")
        assertThat(doc["components"]["schemas"]["LiquidacaoResposta"]["properties"]["valorPagamento"]["type"].asText())
            .isEqualTo("string")
        assertThat(resposta.body()).contains("Idempotency-Key", "valorFaceBrl", "201", "409")
        assertThat(get("/swagger-ui/index.html").statusCode()).isEqualTo(200)
    }

    @Test
    fun `falhas financeiras e cambiais sao 503 sem detalhes internos`() {
        listOf("dados", "cambio").forEach {
            val resposta = get("/test/falhas/$it", tokenA)
            assertThat(resposta.statusCode()).isEqualTo(503)
            assertThat(resposta.tree()["codigo"].asText()).isEqualTo("SERVICO_INDISPONIVEL")
            assertThat(resposta.body()).doesNotContain("detalhe", "Exception", "SQL")
        }
        val inesperada = get("/test/falhas/inesperada", tokenA)
        assertThat(inesperada.statusCode()).isEqualTo(500)
        assertThat(inesperada.tree()["codigo"].asText()).isEqualTo("ERRO_INTERNO")
        assertThat(inesperada.body()).doesNotContain("detalhe", "Exception", "SQL")
    }

    private fun criarCedente(prefixo: String): JsonNode {
        val resposta = post("$prefixo/cedentes", tokenA, """{"nome":"Cedente teste","documento":"${UUID.randomUUID().toString().take(32)}"}""")
        assertThat(resposta.statusCode()).isEqualTo(201)
        return resposta.tree()
    }

    private fun criarRecebivel(prefixo: String, cedente: String, modalidade: String = "DUPLICATA_MERCANTIL",
        valor: String = "100000.00", prazo: Int = 3): JsonNode {
        val resposta = post("$prefixo/recebiveis", tokenA,
            """{"cedenteId":"$cedente","modalidade":"$modalidade","valorFaceBrl":"$valor","prazoMeses":$prazo}""")
        assertThat(resposta.statusCode()).isEqualTo(201)
        return resposta.tree()
    }

    private fun login(email: String, senha: String): String {
        val resposta = post("/api/v1/auth/token", null, json.writeValueAsString(mapOf("email" to email, "password" to senha)))
        assertThat(resposta.statusCode()).isEqualTo(200)
        return resposta.tree()["accessToken"].asText()
    }

    private fun tokenExpirado(): String {
        val agora = Instant.now()
        val claims = JwtClaimsSet.builder().issuer(jwtProperties.issuer).subject(usuarioA.toString())
            .claim("organizacao_id", orgA.toString()).issuedAt(agora.minusSeconds(7200))
            .expiresAt(agora.minusSeconds(3600)).build()
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun get(path: String, token: String? = null) = request("GET", path, token, null, null)
    private fun post(path: String, token: String?, body: String, chave: String? = null) =
        request("POST", path, token, body, chave)
    private fun request(method: String, path: String, token: String?, body: String?, chave: String?): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
        token?.let { builder.header("Authorization", "Bearer $it") }
        chave?.let { builder.header("Idempotency-Key", it) }
        if (body != null) builder.header("Content-Type", "application/json")
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        return http.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
    }
    private fun HttpResponse<String>.tree(): JsonNode = json.readTree(body())

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { TestJwtSecret.value }
        }
    }
}
