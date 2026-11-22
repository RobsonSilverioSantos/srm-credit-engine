package br.com.srm.creditengine.lotes

import br.com.srm.creditengine.TestJwtSecret
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.lotes.adaptador.saida.LotesJdbc
import br.com.srm.creditengine.lotes.aplicacao.ConflitoImportacaoLote
import br.com.srm.creditengine.lotes.aplicacao.ItemImportacao
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
class ImportacaoLoteHttpPostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var encoder: PasswordEncoder
    @Autowired private lateinit var gerenciador: PlatformTransactionManager
    @LocalServerPort private var porta: Int = 0
    private val http = HttpClient.newHttpClient()
    private val json = ObjectMapper()
    private lateinit var org: UUID
    private lateinit var autor: UUID
    private lateinit var cedente: UUID
    private lateinit var token: String

    @BeforeEach
    fun preparar() {
        org = UUID.randomUUID()
        autor = UUID.randomUUID()
        cedente = UUID.randomUUID()
        val email = "$autor@example.invalid"
        val senha = UUID.randomUUID().toString()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'B16')", org)
        jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash) VALUES (?, ?, ?, ?)",
            autor, org, email, encoder.encode(senha))
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'B16', 'doc')", cedente, org)
        val login = http.send(post("/api/v1/auth/token", """{"email":"$email","password":"$senha"}""", "application/json").build(),
            HttpResponse.BodyHandlers.ofString())
        assertThat(login.statusCode()).isEqualTo(200)
        token = json.readTree(login.body())["accessToken"].asText()
    }

    @Test
    fun `arq A e B concorrentes confirmam um lote e reimportacao explicita chega a 396`() {
        val arquivos = listOf(fixture("SRM_LOTE_A_200_RECEBIVEIS.csv"), fixture("SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv"))
        val previas = arquivos.mapIndexed { i, csv -> previa(csv, if (i == 0) "/api/v1" else "/bff/v1") }
        previas.forEach { p ->
            assertThat(p["confirmavel"].asBoolean()).isTrue()
            assertThat(p["itens"].size()).isEqualTo(200)
        }
        semLiquidacao(0)
        val comandos = previas.map(::comandos)
        val execucoes = comandos.mapIndexed { i, body ->
            http.sendAsync(confirmar("em-paralelo-$i", body), HttpResponse.BodyHandlers.ofString())
        }.map { it.get(40, java.util.concurrent.TimeUnit.SECONDS) }
        assertThat(execucoes.map { it.statusCode() }).containsExactlyInAnyOrder(201, 409)
        semLiquidacao(200)
        assertThat(contar("importacoes_recebiveis")).isEqualTo(1)
        val vencedor = execucoes.indexOfFirst { it.statusCode() == 201 }
        val perdedor = 1 - vencedor
        assertThat(json.readTree(execucoes[vencedor].body())["cadastrados"].asInt()).isEqualTo(200)

        val replay = http.send(confirmar("em-paralelo-$vencedor", comandos[vencedor], "/bff/v1"), HttpResponse.BodyHandlers.ofString())
        assertThat(replay.statusCode()).isEqualTo(201)
        assertThat(replay.body()).isEqualTo(execucoes[vencedor].body())
        val divergente = http.send(confirmar("em-paralelo-$vencedor", comandos[perdedor]), HttpResponse.BodyHandlers.ofString())
        assertThat(divergente.statusCode()).isEqualTo(409)

        val novaPrevia = previa(arquivos[perdedor])
        val linhas = novaPrevia["itens"]
        assertThat(linhas.count { it["status"].asText() == "JA_CADASTRADO" }).isEqualTo(4)
        assertThat(linhas.count { it["status"].asText() == "NOVO" }).isEqualTo(196)
        assertThat(novaPrevia["confirmavel"].asBoolean()).isFalse()
        val refs = linhas.filter { it["status"].asText() == "JA_CADASTRADO" }
            .map { it["comando"]["referenciaExterna"].asText() }.toSet()
        val corrigido = arquivos[perdedor].trimEnd().lines().filterIndexed { i, linha ->
            i == 0 || linha.substringBefore(';') !in refs
        }.joinToString("\n")
        val corrigida = previa(corrigido)
        assertThat(corrigida["itens"].size()).isEqualTo(196)
        val importado = http.send(confirmar("corrigido", comandos(corrigida)), HttpResponse.BodyHandlers.ofString())
        assertThat(importado.statusCode()).isEqualTo(201)
        assertThat(json.readTree(importado.body())["cadastrados"].asInt()).isEqualTo(196)
        semLiquidacao(396)
        assertThat(contar("importacoes_recebiveis")).isEqualTo(2)
    }

    @Test
    fun `falha tardia na restricao transacional reverte recebiveis itens e lote anteriores`() {
        val lotes = LotesJdbc(jdbc, TransactionTemplate(gerenciador))
        val identidade = IdentidadeUsuario(autor, org)
        fun item(ref: String) = ItemImportacao(ref, cedente, "DUPLICATA_MERCANTIL", BigDecimal("250.00"),
            2, null, MoedaPagamento.BRL)
        lotes.confirmar(identidade, "primeiro", "a".repeat(64), listOf(item("z-duplicada"))) {}
        assertThrows<ConflitoImportacaoLote> {
            // A validação artificialmente vazia simula referência concorrente surgida depois da leitura de pré-condições.
            lotes.confirmar(identidade, "tardio", "b".repeat(64),
                listOf(item("a-nova"), item("b-nova"), item("z-duplicada"))) {}
        }
        semLiquidacao(1)
        assertThat(contar("importacoes_recebiveis")).isEqualTo(1)
        assertThat(contar("importacoes_recebiveis_itens")).isEqualTo(1)
    }

    @Test
    fun `cedente alheio nao pode ser cadastrado nem produzir efeito parcial`() {
        val outraOrg = UUID.randomUUID()
        val alheio = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'B16 alheio')", outraOrg)
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'B16', 'doc')", alheio, outraOrg)
        val p = previa(VETOR_CSV + "\n" +
            "permitido;$cedente;DUPLICATA_MERCANTIL;100.00;3;;BRL\n" +
            "alheio;$alheio;DUPLICATA_MERCANTIL;100.00;3;;BRL")
        assertThat(p["itens"].map { it["status"].asText() }).containsExactly("NOVO", "INVALIDO")
        assertThat(p["confirmavel"].asBoolean()).isFalse()
        val resposta = http.send(confirmar("alheio", comandos(p)), HttpResponse.BodyHandlers.ofString())
        assertThat(resposta.statusCode()).isEqualTo(422)
        semLiquidacao(0)
        assertThat(contar("importacoes_recebiveis")).isZero()
    }

    private fun confirmar(chave: String, corpo: String, base: String = "/api/v1"): HttpRequest =
        post("$base/lotes/recebiveis", corpo, "application/json")
            .header("Authorization", "Bearer $token").header("Idempotency-Key", chave).build()

    private fun previa(csv: String, base: String = "/api/v1"): JsonNode {
        val fronteira = "lote-${UUID.randomUUID()}"
        val corpo = "--$fronteira\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"teste.csv\"\r\n" +
            "Content-Type: text/csv\r\n\r\n$csv\r\n--$fronteira--\r\n"
        val resposta = http.send(post("$base/lotes/recebiveis/validacoes", corpo, "multipart/form-data; boundary=$fronteira")
            .header("Authorization", "Bearer $token").build(), HttpResponse.BodyHandlers.ofString())
        assertThat(resposta.statusCode()).isEqualTo(200)
        return json.readTree(resposta.body())
    }

    private fun post(rota: String, corpo: String, tipo: String) = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$porta$rota"))
        .timeout(Duration.ofSeconds(30)).header("Content-Type", tipo).POST(HttpRequest.BodyPublishers.ofString(corpo))

    private fun comandos(previa: JsonNode): String =
        json.writeValueAsString(mapOf("itens" to previa["itens"].map { it["comando"] }))

    private fun fixture(nome: String): String {
        val raiz = sequenceOf(Path.of("../.."), Path.of(".."), Path.of("."))
            .map { it.resolve("docs/validacao/lotes").resolve(nome) }.first { Files.isRegularFile(it) }
        return Files.readAllLines(raiz).mapIndexed { i, linha ->
            if (i == 0) linha else linha.split(';').toMutableList().apply { this[1] = cedente.toString() }.joinToString(";")
        }.joinToString("\n")
    }

    private fun contar(tabela: String) =
        checkNotNull(jdbc.queryForObject("SELECT count(*) FROM $tabela WHERE organizacao_id = ?", Int::class.java, org))

    private fun semLiquidacao(titulos: Int) {
        assertThat(contar("recebiveis")).isEqualTo(titulos)
        assertThat(contar("liquidacoes")).isZero()
        assertThat(contar("auditoria_liquidacoes")).isZero()
    }

    companion object {
        private const val VETOR_CSV = "referenciaExterna;cedenteId;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento"

        @JvmStatic
        @DynamicPropertySource
        fun jwt(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { TestJwtSecret.value }
        }
    }
}
