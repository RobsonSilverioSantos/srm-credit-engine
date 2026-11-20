package br.com.srm.creditengine.seguranca

import br.com.srm.creditengine.TestJwtSecret
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ComandoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.LiquidarRecebivel
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.http.ResponseEntity

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(SegurancaPostgresTest.TestEndpoints::class)
class SegurancaPostgresTest {
    @TestConfiguration(proxyBeanMethods = false)
    class TestEndpoints {
        @Bean
        fun securityTestEndpoints(contexto: ContextoOrganizacao, cadastro: Cadastro, liquidar: LiquidarRecebivel) =
            SecurityTestEndpoints(contexto, cadastro, liquidar)
    }

    @RestController
    class SecurityTestEndpoints(
        private val contexto: ContextoOrganizacao,
        private val cadastro: Cadastro,
        private val liquidar: LiquidarRecebivel,
    ) {
        @GetMapping("/test/identity")
        fun identity(): Map<String, UUID> = mapOf("organizacaoId" to contexto.organizacaoId())

        @GetMapping("/test/cedentes/{id}")
        fun cedente(@PathVariable id: UUID): ResponseEntity<Any> =
            cadastro.buscarCedente(id)?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

        @GetMapping("/test/liquidar/{id}")
        fun liquidar(@PathVariable id: UUID): Map<String, UUID> =
            mapOf("id" to liquidar.liquidar(ComandoLiquidacao(id, MoedaPagamento.BRL, "teste")).liquidacaoId)
    }

    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var passwordEncoder: PasswordEncoder
    @Autowired private lateinit var taxas: TaxasPorta
    @Autowired private lateinit var properties: br.com.srm.creditengine.seguranca.adaptador.entrada.JwtProperties
    @LocalServerPort private var port: Int = 0
    private val httpClient = HttpClient.newHttpClient()
    private val objectMapper = ObjectMapper()

    private lateinit var organizacaoA: UUID
    private lateinit var organizacaoB: UUID
    private lateinit var usuarioA: UUID
    private lateinit var usuarioB: UUID
    private lateinit var usuarioInativo: UUID
    private lateinit var emailA: String
    private lateinit var emailB: String
    private lateinit var emailInativo: String
    private lateinit var senhaA: String
    private lateinit var senhaB: String
    private lateinit var cedenteA: UUID
    private lateinit var cedenteB: UUID

    @BeforeEach
    fun prepararUsuariosEOrganizacoes() {
        organizacaoA = UUID.randomUUID()
        organizacaoB = UUID.randomUUID()
        usuarioA = UUID.randomUUID()
        usuarioB = UUID.randomUUID()
        usuarioInativo = UUID.randomUUID()
        emailA = "usuario-${UUID.randomUUID()}@example.invalid"
        emailB = "usuario-${UUID.randomUUID()}@example.invalid"
        emailInativo = "usuario-${UUID.randomUUID()}@example.invalid"
        senhaA = UUID.randomUUID().toString()
        senhaB = UUID.randomUUID().toString()
        cedenteA = UUID.randomUUID()
        cedenteB = UUID.randomUUID()

        jdbc.update(
            "INSERT INTO organizacoes (id, nome) VALUES (?, ?), (?, ?)",
            organizacaoA, "Organização A $organizacaoA", organizacaoB, "Organização B $organizacaoB",
        )
        jdbc.update(
            """INSERT INTO usuarios (id, organizacao_id, email, senha_hash)
               VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?)""",
            usuarioA, organizacaoA, emailA, passwordEncoder.encode(senhaA),
            usuarioB, organizacaoB, emailB, passwordEncoder.encode(senhaB),
            usuarioInativo, organizacaoA, emailInativo, passwordEncoder.encode(UUID.randomUUID().toString()),
        )
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE id = ?", usuarioInativo)
        jdbc.update(
            """INSERT INTO cedentes (id, organizacao_id, nome, documento)
               VALUES (?, ?, ?, ?), (?, ?, ?, ?)""",
            cedenteA, organizacaoA, "Cedente A", "doc-a-${cedenteA.toString().take(20)}",
            cedenteB, organizacaoB, "Cedente B", "doc-b-${cedenteB.toString().take(20)}",
        )
    }

    @Test
    fun `autentica com senha BCrypt e deriva a organizacao do usuario persistido`() {
        val hash = jdbc.queryForObject(
            "SELECT senha_hash FROM usuarios WHERE id = ?",
            String::class.java,
            usuarioA,
        )!!
        assertThat(hash).startsWith("\$2")
        assertThat(hash).isNotEqualTo(senhaA)
        assertThat(passwordEncoder.matches(senhaA, hash)).isTrue()

        val token = autenticar(emailA, senhaA)
        assertThat(organizacaoDo(token)).isEqualTo(organizacaoA)
        assertThat(organizacaoDo(autenticar(emailB, senhaB))).isEqualTo(organizacaoB)
    }

    @Test
    fun `liquidacao usa principal validado para escopo e auditoria`() {
        val instante = Instant.now().truncatedTo(ChronoUnit.MICROS).minusSeconds(1)
        taxas.registrarTaxaBase(BigDecimal("0.01"), instante)
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), instante)
        val recebivel = UUID.randomUUID()
        jdbc.update("""INSERT INTO recebiveis
            (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
            VALUES (?, ?, ?, ?, 100000, 3)""", recebivel, organizacaoA, cedenteA, Modalidades.DUPLICATA_MERCANTIL)
        assertThat(requisicao("GET", "/test/liquidar/$recebivel").statusCode()).isEqualTo(401)
        val resposta = requisicao("GET", "/test/liquidar/$recebivel", autenticar(emailA, senhaA))
        assertThat(resposta.statusCode()).isEqualTo(200)
        val id = UUID.fromString(objectMapper.readTree(resposta.body()).get("id").asText())
        val auditoria = jdbc.queryForMap("SELECT organizacao_id, usuario_id FROM auditoria_liquidacoes WHERE liquidacao_id = ?", id)
        assertThat(auditoria["organizacao_id"]).isEqualTo(organizacaoA)
        assertThat(auditoria["usuario_id"]).isEqualTo(usuarioA)
    }

    @Test
    fun `rejeita senha incorreta usuario inexistente e usuario inativo`() {
        assertThat(respostaLogin(emailA, UUID.randomUUID().toString()).statusCode()).isEqualTo(401)
        assertThat(respostaLogin("nao-existe-${UUID.randomUUID()}@example.invalid", senhaA).statusCode())
            .isEqualTo(401)
        assertThat(respostaLogin(emailInativo, senhaA).statusCode()).isEqualTo(401)
    }

    @Test
    fun `rejeita requisicao sem autenticacao`() {
        assertThat(requisicao("GET", "/test/identity").statusCode()).isEqualTo(401)
    }

    @Test
    fun `rejeita token expirado adulterado ou com algoritmo nao permitido`() {
        val token = token(usuarioA, organizacaoA, Instant.now().plusSeconds(300))
        val expirado = token(usuarioA, organizacaoA, Instant.now().minusSeconds(1))
        val partes = token.split(".")
        val assinaturaAdulterada = partes[0] + "." + partes[1] + "." +
            (if (partes[2].first() == 'A') "B" else "A") + partes[2].drop(1)
        val algoritmoNaoPermitido = token(
            usuarioA,
            organizacaoA,
            Instant.now().plusSeconds(300),
            algoritmo = "HS384",
        )

        assertThat(respostaIdentidade(expirado).statusCode()).isEqualTo(401)
        assertThat(respostaIdentidade(assinaturaAdulterada).statusCode()).isEqualTo(401)
        assertThat(respostaIdentidade(algoritmoNaoPermitido).statusCode()).isEqualTo(401)
    }

    @Test
    fun `bloqueia dados de outra organizacao e nao compartilha contexto entre requisicoes`() {
        val tokenA = autenticar(emailA, senhaA)
        val tokenB = autenticar(emailB, senhaB)

        assertThat(respostaCedente(cedenteB, tokenA).statusCode()).isEqualTo(404)
        assertThat(respostaCedente(cedenteB, tokenB).statusCode()).isEqualTo(200)
        assertThat(organizacaoDo(tokenA)).isEqualTo(organizacaoA)
        assertThat(organizacaoDo(tokenB)).isEqualTo(organizacaoB)
        assertThat(organizacaoDo(tokenA)).isEqualTo(organizacaoA)

        val executor = Executors.newFixedThreadPool(4)
        try {
            val respostas = (0 until 16).map { indice ->
                val token = if (indice % 2 == 0) tokenA else tokenB
                val organizacaoEsperada = if (indice % 2 == 0) organizacaoA else organizacaoB
                CompletableFuture.supplyAsync({ organizacaoDo(token) to organizacaoEsperada }, executor)
            }
            assertThat(respostas.map { it.get(10, TimeUnit.SECONDS).let { resultado -> resultado.first == resultado.second } })
                .containsOnly(true)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `revoga token quando usuario ou organizacao ficam inativos`() {
        val tokenA = autenticar(emailA, senhaA)
        jdbc.update("UPDATE usuarios SET ativo = FALSE WHERE id = ?", usuarioA)
        assertThat(respostaIdentidade(tokenA).statusCode()).isEqualTo(401)

        val tokenB = autenticar(emailB, senhaB)
        jdbc.update("UPDATE organizacoes SET ativa = FALSE WHERE id = ?", organizacaoB)
        assertThat(respostaIdentidade(tokenB).statusCode()).isEqualTo(401)
    }

    private fun autenticar(email: String, senha: String): String {
        val resposta = respostaLogin(email, senha)
        assertThat(resposta.statusCode()).isEqualTo(200)
        assertThat(resposta.headers().firstValue("Set-Cookie").orElse("")).doesNotContain("JSESSIONID")
        return objectMapper.readTree(resposta.body()).get("accessToken").asText()
    }

    private fun respostaLogin(email: String, senha: String) =
        requisicao(
            "POST",
            "/api/v1/auth/token",
            contentType = MediaType.APPLICATION_JSON,
            body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to senha)),
        )

    private fun respostaIdentidade(token: String) =
        requisicao("GET", "/test/identity", token)

    private fun respostaCedente(id: UUID, token: String) =
        requisicao("GET", "/test/cedentes/$id", token)

    private fun organizacaoDo(token: String): UUID =
        objectMapper.readTree(respostaIdentidade(token).body())
            .get("organizacaoId").asText().let(UUID::fromString)

    private fun requisicao(
        metodo: String,
        caminho: String,
        token: String? = null,
        contentType: MediaType? = null,
        body: String? = null,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$caminho"))
            .header("Accept", MediaType.APPLICATION_JSON_VALUE)
        token?.let { builder.header("Authorization", "Bearer $it") }
        contentType?.let { builder.header("Content-Type", it.toString()) }
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        val request = builder.method(metodo, publisher).build()
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun token(
        usuarioId: UUID,
        organizacaoId: UUID,
        expiraEm: Instant,
        algoritmo: String = "HS256",
    ): String {
        val header = mapOf("alg" to algoritmo, "typ" to "JWT")
        val claims = mapOf(
            "iss" to properties.issuer,
            "sub" to usuarioId.toString(),
            "organizacao_id" to organizacaoId.toString(),
            "iat" to Instant.now().epochSecond,
            "exp" to expiraEm.epochSecond,
        )
        val base64 = Base64.getUrlEncoder().withoutPadding()
        val entrada = "${base64.encodeToString(objectMapper.writeValueAsBytes(header))}." +
            base64.encodeToString(objectMapper.writeValueAsBytes(claims))
        val algoritmoMac = when (algoritmo) {
            "HS256" -> "HmacSHA256"
            "HS384" -> "HmacSHA384"
            else -> error("Algoritmo de teste inesperado")
        }
        val mac = Mac.getInstance(algoritmoMac)
        mac.init(SecretKeySpec(TestJwtSecret.value.toByteArray(StandardCharsets.UTF_8), algoritmoMac))
        return "$entrada.${base64.encodeToString(mac.doFinal(entrada.toByteArray(StandardCharsets.US_ASCII)))}"
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { TestJwtSecret.value }
        }
    }
}
