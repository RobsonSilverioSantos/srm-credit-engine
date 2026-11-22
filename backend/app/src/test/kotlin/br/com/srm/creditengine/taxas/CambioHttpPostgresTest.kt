package br.com.srm.creditengine.taxas

import br.com.srm.creditengine.TestJwtSecret
import br.com.srm.creditengine.seguranca.adaptador.entrada.JwtProperties
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.sun.net.httpserver.HttpServer
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["srm.cambio.modo-demo=false"])
class CambioHttpPostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var jwt: JwtEncoder
    @Autowired private lateinit var properties: JwtProperties
    @Autowired private lateinit var taxas: TaxasPorta
    @LocalServerPort private var porta: Int = 0
    private val cliente = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private lateinit var org: UUID
    private lateinit var master: String
    private lateinit var admin: String
    private lateinit var operador: String
    private lateinit var outro: String
    private lateinit var masterId: UUID

    @BeforeEach
    fun preparar() {
        org = UUID.randomUUID()
        val orgB = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'B15 HTTP'), (?, 'Outra')", org, orgB)
        masterId = UUID.randomUUID()
        master = usuario(org, "MASTER", masterId)
        admin = usuario(org, "ADMIN")
        operador = usuario(org, "OPERADOR")
        outro = usuario(orgB, "MASTER")
        val corte = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS)
        taxas.registrarTaxaBase(BigDecimal("0.01"), corte)
        taxas.registrarSpread("DUPLICATA_MERCANTIL", BigDecimal("0.015"), corte)
        statusBcb.set(200)
    }

    private fun usuario(organizacao: UUID, perfil: String, id: UUID = UUID.randomUUID()): String {
        jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash, perfil) VALUES (?, ?, ?, 'hash', ?)",
            id, organizacao, "$id@example.invalid", perfil)
        val claims = JwtClaimsSet.builder().issuer(properties.issuer).subject(id.toString())
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(600))
            .claim("organizacao_id", organizacao.toString()).build()
        return jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }

    private fun request(
        metodo: String, caminho: String, token: String? = master, corpo: String = "", chave: String? = null,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$porta$caminho"))
            .header("Content-Type", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (chave != null) builder.header("Idempotency-Key", chave)
        return cliente.send(builder.method(metodo, HttpRequest.BodyPublishers.ofString(corpo)).build(),
            HttpResponse.BodyHandlers.ofString())
    }
    private fun HttpResponse<String>.tree(): JsonNode = json.readTree(body())
    private fun manual(token: String = master): JsonNode {
        val r = request("POST", "/api/v1/cotacoes-cambiais/manuais", token,
            """{"taxaBrlPorUsd":"6.1000","justificativa":"Contingencia explicitamente aprovada"}""")
        assertThat(r.statusCode()).isEqualTo(201)
        return r.tree()
    }
    private fun selecionar(id: String?, fonte: String = "MANUAL", token: String = master, base: String = "/api/v1") =
        request("POST", "$base/cotacoes-cambiais/selecoes", token,
            """{"fonte":"$fonte","cotacaoId":${id?.let { "\"$it\"" } ?: "null"},"justificativa":"Selecao autorizada"}""")
    private fun simular(token: String = operador, moeda: String = "USD", base: String = "/api/v1") =
        request("POST", "$base/simulacoes", token,
            """{"modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":"100000.00","prazoMeses":3,"moeda":"$moeda"}""")

    @Test
    fun `RBAC real API BFF cadastro separado de autorizacao autoria confiavel e consumo operador`() {
        for (base in listOf("/api/v1", "/bff/v1")) {
            val body = """{"taxaBrlPorUsd":"6.1","justificativa":"Contingencia"}"""
            assertThat(request("POST", "$base/cotacoes-cambiais/manuais", null, body).statusCode()).isEqualTo(401)
            assertThat(request("POST", "$base/cotacoes-cambiais/manuais", operador, body).statusCode()).isEqualTo(403)
            assertThat(request("POST", "$base/cotacoes-cambiais/ptax/sincronizacoes", operador).statusCode()).isEqualTo(403)
        }
        val m = manual()
        assertThat(m["fonte"].asText()).isEqualTo("MANUAL")
        assertThat(m["autorId"].asText()).isEqualTo(masterId.toString())
        val id = m["id"].asText()
        assertThat(selecionar(id, token = operador).statusCode()).isEqualTo(403)
        assertThat(selecionar(id, token = outro).statusCode()).isEqualTo(409)
        val ativacao = selecionar(id, token = admin, base = "/bff/v1")
        assertThat(ativacao.statusCode()).isEqualTo(201)
        assertThat(ativacao.tree()["validaAte"].asText()).endsWith("03:00:00Z")
        val s = simular(base = "/bff/v1")
        assertThat(s.statusCode()).isEqualTo(200)
        assertThat(s.tree()["fonteCambial"].asText()).isEqualTo("MANUAL")
        assertThat(s.tree()["cotacaoBrlPorUsd"].isTextual).isTrue()
        assertThat(s.tree()["valorPresenteBrl"].asText()).isEqualTo("92859.94")
        assertThat(s.tree()["revisaoCambial"].asText()).hasSize(64)
        assertThat(selecionar(id).statusCode()).isEqualTo(409)
        val historico = request("GET", "/bff/v1/cotacoes-cambiais/selecoes", operador).tree()
        assertThat(historico["itens"][0]["id"].asText()).isEqualTo(ativacao.tree()["id"].asText())
        assertThat(historico["itens"][0]["autorId"].isNull).isTrue()
        assertThat(historico["itens"][0]["justificativa"].isNull).isTrue()
        assertThat(request("GET", "/api/v1/cotacoes-cambiais/selecoes", outro).tree()["total"].asInt()).isZero()
    }

    @Test
    fun `validacao estrita rejeita numero JSON campos injetados escalas e vigencias controladas pelo cliente`() {
        val corpos = listOf(
            """{"taxaBrlPorUsd":6.1,"justificativa":"x"}""",
            """{"taxaBrlPorUsd":"0","justificativa":"x"}""",
            """{"taxaBrlPorUsd":"-1","justificativa":"x"}""",
            """{"taxaBrlPorUsd":"1.12345678901","justificativa":"x"}""",
            """{"taxaBrlPorUsd":"10000000000","justificativa":"x"}""",
            """{"taxaBrlPorUsd":"6.1","justificativa":" "}""",
            """{"taxaBrlPorUsd":"6.1","justificativa":"x","fonte":"BCB_PTAX"}""",
            """{"taxaBrlPorUsd":"6.1","justificativa":"x","organizacaoId":"$org"}""",
            """{"taxaBrlPorUsd":"6.1","justificativa":"x","autorId":"$masterId"}""",
            """{"taxaBrlPorUsd":"6.1","justificativa":"x","vigenteEm":"2026-01-01T00:00:00Z"}""",
        )
        corpos.forEach {
            assertThat(request("POST", "/bff/v1/cotacoes-cambiais/manuais", master, it).statusCode()).isEqualTo(400)
        }
        assertThat(selecionar(null, "DEMO").statusCode()).isEqualTo(400)
        assertThat(selecionar(null).statusCode()).isEqualTo(400)
        assertThat(selecionar(UUID.randomUUID().toString(), "BCB_PTAX").statusCode()).isEqualTo(400)
        assertThat(request("GET", "/api/v1/cotacoes-cambiais?pagina=-1", operador).statusCode()).isEqualTo(400)
        assertThat(request("GET", "/api/v1/cotacoes-cambiais?tamanho=101", operador).statusCode()).isEqualTo(400)
    }

    @Test
    fun `historico e paginado imutavel isolado por organizacao e oculta auditoria do operador`() {
        val m = manual()
        val id = m["id"].asText()
        val adminPage = request("GET", "/api/v1/cotacoes-cambiais?fonte=MANUAL&tamanho=1", admin)
        assertThat(adminPage.statusCode()).isEqualTo(200)
        assertThat(adminPage.tree()["total"].asInt()).isEqualTo(1)
        assertThat(adminPage.tree()["itens"][0]["autorId"].asText()).isEqualTo(masterId.toString())
        val oper = request("GET", "/bff/v1/cotacoes-cambiais?fonte=MANUAL", operador).tree()
        assertThat(oper["itens"][0]["autorId"].isNull).isTrue()
        assertThat(oper["itens"][0]["justificativa"].isNull).isTrue()
        assertThat(request("GET", "/api/v1/cotacoes-cambiais?fonte=MANUAL", outro).tree()["total"].asInt()).isZero()
        assertThat(request("GET", "/api/v1/cotacoes-cambiais?fonte=MANUAL&pagina=1&tamanho=1", admin)
            .tree()["itens"].size()).isZero()
        assertThat(request("DELETE", "/api/v1/cotacoes-cambiais/manuais/$id").statusCode()).isEqualTo(404)
        assertThat(request("PUT", "/bff/v1/cotacoes-cambiais/manuais/$id", corpo = "{}").statusCode()).isEqualTo(404)
    }

    @Test
    fun `sincronizacao BCB e fonte padrao sem fallback funciona com persistida local durante indisponibilidade`() {
        val r = request("POST", "/api/v1/cotacoes-cambiais/ptax/sincronizacoes", master)
        assertThat(r.statusCode()).isEqualTo(200)
        manual()
        val s = simular()
        assertThat(s.statusCode()).isEqualTo(200)
        assertThat(s.tree()["fonteCambial"].asText()).isEqualTo("BCB_PTAX")
        assertThat(s.tree()["cotacaoBrlPorUsd"].asText()).isEqualTo("5.8889000000")
        val chamadasAntes = chamadas.get()
        statusBcb.set(503)
        assertThat(simular().statusCode()).isEqualTo(200)
        assertThat(chamadas.get()).isEqualTo(chamadasAntes)
        assertThat(request("POST", "/bff/v1/cotacoes-cambiais/ptax/sincronizacoes", admin).statusCode()).isEqualTo(503)
        assertThat(simular().tree()["fonteCambial"].asText()).isEqualTo("BCB_PTAX")
        assertThat(simular(moeda = "BRL").statusCode()).isEqualTo(200)
    }

    @Test
    fun `liquidacao requer revisao da fonte e replay nao consulta nem reprecifica politica posterior`() {
        val m = manual()
        assertThat(selecionar(m["id"].asText()).statusCode()).isEqualTo(201)
        val simulacao = simular().tree()
        val cedente = UUID.randomUUID()
        val recebivel = UUID.randomUUID()
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'B15', 'legado')", cedente, org)
        jdbc.update("""INSERT INTO recebiveis (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
            VALUES (?, ?, ?, 'DUPLICATA_MERCANTIL', 100000, 3)""", recebivel, org, cedente)
        val path = "/api/v1/recebiveis/$recebivel/liquidacoes"
        assertThat(request("POST", path, operador, """{"moeda":"USD"}""", "sem-previa").statusCode()).isEqualTo(409)
        val corpo = """{"moeda":"USD","revisaoCambial":"${simulacao["revisaoCambial"].asText()}"}"""
        val liquidada = request("POST", path, operador, corpo, "replay")
        assertThat(liquidada.statusCode()).isEqualTo(201)
        assertThat(liquidada.tree()["valorPagamento"].asText()).isEqualTo(simulacao["valorPagamento"].asText())
        assertThat(selecionar(null, "BCB_PTAX").statusCode()).isEqualTo(201)
        assertThat(request("POST", path.replace("/api/", "/bff/"), operador, corpo, "replay").body()).isEqualTo(liquidada.body())
        assertThat(request("POST", path, operador, """{"moeda":"BRL"}""", "replay").statusCode()).isEqualTo(409)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM liquidacoes WHERE organizacao_id = ?", Int::class.java, org))
            .isEqualTo(1)
        val snapshot = jdbc.queryForObject("SELECT cambio_snapshot::text FROM liquidacoes WHERE organizacao_id = ?",
            String::class.java, org)!!
        assertThat(snapshot).contains("MANUAL", masterId.toString(), "Contingencia", "autorizacaoId")
        val extrato = request("GET", "/bff/v1/extrato", operador).tree()["itens"][0]
        assertThat(extrato["fonteCambial"].asText()).isEqualTo("MANUAL")
        assertThat(extrato["autorizacaoCambialId"].isTextual).isTrue()
        assertThat(extrato.has("justificativa")).isFalse()
    }

    @Test
    fun `wiring real do lote usa MANUAL autorizada rejeita troca apos previa e preserva replay`() {
        assertThat(selecionar(manual()["id"].asText()).statusCode()).isEqualTo(201)
        val cedente = UUID.randomUUID()
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'B15 lote', 'legado')",
            cedente, org)
        val linhas = (1..3).joinToString(",") {
            """{"referenciaExterna":"b15-http-$it","cedenteId":"$cedente","modalidade":"DUPLICATA_MERCANTIL",
                "valorFaceBrl":"100000.00","prazoMeses":3,"vencimento":null,"moedaPagamento":"USD"}"""
        }
        val cadastro = request("POST", "/api/v1/lotes/recebiveis", operador, """{"itens":[$linhas]}""", "cadastro-lote")
        assertThat(cadastro.statusCode()).isEqualTo(201)
        val lote = cadastro.tree()["loteId"].asText()
        val itens = cadastro.tree()["itens"].joinToString(",") {
            """{"recebivelId":"${it["recebivelId"].asText()}","moedaPagamento":"USD"}"""
        }
        val path = "/bff/v1/lotes/$lote"
        fun previa(): JsonNode {
            val resposta = request("POST", "$path/previas-liquidacao", operador, """{"itens":[$itens]}""")
            assertThat(resposta.statusCode()).isEqualTo(200)
            assertThat(resposta.tree()["aptoParaLiquidar"].asBoolean()).isTrue()
            return resposta.tree()
        }
        fun corpo(hash: String) = """{"itens":[$itens],"hashPrevia":"$hash","confirmacaoFinanceira":true}"""
        val primeira = previa()
        assertThat(primeira["itens"].all { it["fonteCambio"].asText() == "MANUAL" }).isTrue()
        assertThat(selecionar(manual()["id"].asText()).statusCode()).isEqualTo(201)
        assertThat(request("POST", "$path/liquidacoes", operador, corpo(primeira["hashPrevia"].asText()), "obsoleta")
            .statusCode()).isEqualTo(409)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM liquidacoes WHERE organizacao_id = ?", Int::class.java, org))
            .isZero()
        val atual = previa()
        val comando = corpo(atual["hashPrevia"].asText())
        val confirmada = request("POST", "$path/liquidacoes", operador, comando, "confirmar")
        assertThat(confirmada.statusCode()).isEqualTo(201)
        assertThat(selecionar(null, "BCB_PTAX").statusCode()).isEqualTo(201)
        assertThat(request("POST", "$path/liquidacoes", operador, comando, "confirmar").body()).isEqualTo(confirmada.body())
        assertThat(jdbc.queryForObject("""SELECT count(*) FROM liquidacoes WHERE organizacao_id = ?
            AND cambio_snapshot->>'fonte' = 'MANUAL' AND selecao_cambial_id IS NOT NULL""", Int::class.java, org)).isEqualTo(3)
    }

    @Test
    fun `OpenAPI expoe contratos B15 nas duas superficies com RBAC e decimais textuais`() {
        val doc = request("GET", "/v3/api-docs", null).tree()
        for (base in listOf("/api/v1", "/bff/v1")) {
            assertThat(doc["paths"].has("$base/cotacoes-cambiais/manuais")).isTrue()
            assertThat(doc["paths"].has("$base/cotacoes-cambiais/selecoes")).isTrue()
            assertThat(doc["paths"].has("$base/cotacoes-cambiais/ptax/sincronizacoes")).isTrue()
            assertThat(doc["paths"]["$base/cotacoes-cambiais/manuais"]["post"]["responses"].has("403")).isTrue()
        }
        assertThat(doc["components"]["schemas"]["CotacaoManualEntrada"]["properties"]["taxaBrlPorUsd"]["type"].asText())
            .isEqualTo("string")
    }

    companion object {
        private val statusBcb = AtomicInteger(200)
        private val chamadas = AtomicInteger()
        private val bcb = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { req ->
                chamadas.incrementAndGet()
                val local = Instant.now().minusSeconds(3600).atZone(ZoneId.of("America/Sao_Paulo"))
                    .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"))
                val corpo = """{"value":[{"cotacaoCompra":5.8883,"cotacaoVenda":5.8889,"dataHoraCotacao":"$local","tipoBoletim":"Fechamento"}]}"""
                    .toByteArray()
                req.sendResponseHeaders(statusBcb.get(), corpo.size.toLong())
                req.responseBody.use { it.write(corpo) }
            }
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun configurar(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { TestJwtSecret.value }
            registry.add("srm.cambio.ptax.url") { "http://127.0.0.1:${bcb.address.port}/" }
            registry.add("srm.cambio.ptax.atraso") { "PT0S" }
        }

        @JvmStatic
        @AfterAll
        fun encerrar() { bcb.stop(0) }
    }
}
