package br.com.srm.creditengine.http

import br.com.srm.creditengine.TestJwtSecret
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.seguranca.adaptador.entrada.JwtProperties
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.simulacao.aplicacao.ServicoSimulacao
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.ComandoSimulacao
import br.com.srm.creditengine.taxas.adaptador.saida.ConsultaCambialIndisponivel
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private lateinit var orgB: UUID
    private lateinit var usuarioA: UUID
    private lateinit var tokenA: String
    private lateinit var tokenB: String

    @BeforeEach
    fun preparar() {
        orgA = UUID.randomUUID()
        orgB = UUID.randomUUID()
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
        val extrato = get("/api/v1/extrato", tokenA)
        assertThat(extrato.statusCode()).isEqualTo(200)
        val itens = extrato.tree()["itens"]
        val item = itens.find { it["liquidacaoId"].asText() == terceiro.tree()["liquidacaoId"].asText() }!!
        listOf("recebivelId", "moeda", "valorPresenteBrl", "desagioBrl", "valorPagamento", "liquidadaEm").forEach {
            assertThat(item[it]).isEqualTo(terceiro.tree()[it])
        }
        assertThat(item["taxaBrlPorUnidade"].asText()).isEqualTo("5.4321000000")
        assertThat(item["taxaBaseMensal"].asText()).isEqualTo("0.0100000000")
        assertThat(item["spreadMensal"].asText()).isEqualTo("0.0150000000")
        taxas.registrarTaxaBase(BigDecimal("0.08"), instante.plusSeconds(1))
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.09"), instante.plusSeconds(1))
        taxas.registrarCotacao(BigDecimal("9.00"), instante.plusSeconds(1))
        val depois = get("/api/v1/extrato", tokenA).tree()["itens"]
            .find { it["liquidacaoId"] == item["liquidacaoId"] }
        assertThat(depois).isEqualTo(item)
    }

    @Test
    fun `liquidacoes concorrentes pela API e BFF retornam 201 e 409`() {
        val instante = Instant.now().truncatedTo(ChronoUnit.MICROS)
        taxas.registrarTaxaBase(BigDecimal("0.01"), instante)
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), instante)
        val cedente = criarCedente("/api/v1")["id"].asText()
        val recebivel = criarRecebivel("/api/v1", cedente)["id"].asText()
        val inicio = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val respostas = listOf("api", "bff").map { prefixo ->
                executor.submit<HttpResponse<String>> {
                    check(inicio.await(10, TimeUnit.SECONDS))
                    post("/$prefixo/v1/recebiveis/$recebivel/liquidacoes", tokenA, """{"moeda":"BRL"}""", prefixo)
                }
            }
            inicio.countDown()
            assertThat(respostas.map { it.get(30, TimeUnit.SECONDS).statusCode() }).containsExactlyInAnyOrder(201, 409)
            assertThat(jdbc.queryForObject("SELECT count(*) FROM liquidacoes WHERE organizacao_id = ? AND recebivel_id = ?",
                Int::class.java, orgA, UUID.fromString(recebivel))).isEqualTo(1)
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_liquidacoes WHERE organizacao_id = ?",
                Int::class.java, orgA)).isEqualTo(1)
            assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotencia WHERE organizacao_id = ? AND situacao = 'CONCLUIDA'",
                Int::class.java, orgA)).isEqualTo(1)
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `replay de resposta historica preserva status e corpo na API e BFF`() {
        val instante = Instant.now().truncatedTo(ChronoUnit.MICROS)
        taxas.registrarTaxaBase(BigDecimal("0.01"), instante)
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), instante)
        val cedente = criarCedente("/api/v1")["id"].asText()
        val primeiroId = criarRecebivel("/api/v1", cedente)["id"].asText()
        val primeiro = post("/api/v1/recebiveis/$primeiroId/liquidacoes", tokenA, """{"moeda":"BRL"}""", "original")
        assertThat(primeiro.statusCode()).isEqualTo(201)
        val id = UUID.fromString(criarRecebivel("/api/v1", cedente)["id"].asText())
        val liquidacaoId = UUID.randomUUID()
        jdbc.update("""INSERT INTO liquidacoes
            (id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses, versao_recebivel,
             taxa_base_id, taxa_base_mensal, taxa_base_vigente_em, spread_id, spread_mensal, spread_vigente_em,
             valor_presente_brl, desagio_brl, moeda_pagamento, cotacao_cambial_id, taxa_brl_por_unidade,
             cotacao_vigente_em, cotacao_valida_ate, valor_pagamento, liquidada_em)
            SELECT ?, organizacao_id, ?, modalidade, valor_face_brl, prazo_meses, versao_recebivel,
             taxa_base_id, taxa_base_mensal, taxa_base_vigente_em, spread_id, spread_mensal, spread_vigente_em,
             valor_presente_brl, desagio_brl, moeda_pagamento, cotacao_cambial_id, taxa_brl_por_unidade,
             cotacao_vigente_em, cotacao_valida_ate, valor_pagamento, liquidada_em
            FROM liquidacoes WHERE id = ?""", liquidacaoId, id, UUID.fromString(primeiro.tree()["liquidacaoId"].asText()))
        jdbc.update("UPDATE recebiveis SET status = 'LIQUIDADO', versao = 1 WHERE id = ?", id)
        val hash = MessageDigest.getInstance("SHA-256").digest(
            "LIQUIDAR_RECEBIVEL\n$id\nBRL".toByteArray(StandardCharsets.UTF_8),
        )
        jdbc.update("""INSERT INTO idempotencia
            (id, organizacao_id, tipo_operacao, chave, hash_requisicao, situacao, liquidacao_id,
             resposta_http, resposta_corpo, concluido_em)
            SELECT ?, organizacao_id, tipo_operacao, 'historico', ?, 'CONCLUIDA', ?, 202,
                jsonb_set(jsonb_set(resposta_corpo, '{liquidacaoId}', to_jsonb(CAST(? AS text))),
                          '{recebivelId}', to_jsonb(CAST(? AS text))), CURRENT_TIMESTAMP
            FROM idempotencia WHERE organizacao_id = ? AND chave = 'original'""",
            UUID.randomUUID(), hash, liquidacaoId, liquidacaoId.toString(), id.toString(), orgA)
        val armazenado = jdbc.queryForObject(
            "SELECT resposta_corpo::text FROM idempotencia WHERE organizacao_id = ? AND chave = 'historico'",
            String::class.java, orgA,
        )
        val api = post("/api/v1/recebiveis/$id/liquidacoes", tokenA, """{"moeda":"BRL"}""", "historico")
        val bff = post("/bff/v1/recebiveis/$id/liquidacoes", tokenA, """{"moeda":"BRL"}""", "historico")
        assertThat(api.statusCode()).isEqualTo(202)
        assertThat(bff.statusCode()).isEqualTo(202)
        assertThat(api.tree()).isEqualTo(json.readTree(armazenado))
        assertThat(bff.body()).isEqualTo(api.body())
    }

    @Test
    fun `extrato filtra pagina e isola organizacoes com ordenacao deterministica`() {
        val referencia = Instant.now().truncatedTo(ChronoUnit.MICROS).minus(10, ChronoUnit.DAYS)
        val base = taxas.registrarTaxaBase(BigDecimal("0.01"), referencia)
        val spread = taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), referencia)
        val cotacao = taxas.registrarCotacao(BigDecimal("5.4321"), referencia)
        val dia = LocalDate.now(ZoneOffset.UTC).minusDays(2)
        val cedenteA = UUID.randomUUID()
        val outroCedenteA = UUID.randomUUID()
        val cedenteB = UUID.randomUUID()
        listOf(cedenteA to orgA, outroCedenteA to orgA, cedenteB to orgB).forEach { (id, org) ->
            jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'Teste', ?)",
                id, org, id.toString().take(32))
        }
        fun inserir(org: UUID, cedente: UUID, moeda: String, data: Instant): UUID {
            val recebivel = UUID.randomUUID()
            val liquidacao = UUID.randomUUID()
            jdbc.update("""INSERT INTO recebiveis
                (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses, status, versao)
                VALUES (?, ?, ?, 'DUPLICATA_MERCANTIL', 100000, 3, 'LIQUIDADO', 1)""", recebivel, org, cedente)
            jdbc.update("""INSERT INTO liquidacoes
                (id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses, versao_recebivel,
                 taxa_base_id, taxa_base_mensal, taxa_base_vigente_em, spread_id, spread_mensal, spread_vigente_em,
                 valor_presente_brl, desagio_brl, moeda_pagamento, cotacao_cambial_id, taxa_brl_por_unidade,
                 cotacao_vigente_em, cotacao_valida_ate, valor_pagamento, liquidada_em)
                VALUES (?, ?, ?, 'DUPLICATA_MERCANTIL', 100000, 3, 1, ?, 0.01, ?, ?, 0.015, ?,
                        92859.94, 7140.06, ?, ?, ?, ?, ?, ?, ?)""",
                liquidacao, org, recebivel, base.id, referencia.atOffset(ZoneOffset.UTC),
                spread.id, referencia.atOffset(ZoneOffset.UTC), moeda,
                if (moeda == "USD") cotacao.id else null,
                if (moeda == "USD") BigDecimal("5.4321") else null,
                if (moeda == "USD") referencia.atOffset(ZoneOffset.UTC) else null,
                if (moeda == "USD") data.plusSeconds(60).atOffset(ZoneOffset.UTC) else null,
                if (moeda == "USD") BigDecimal("17094.67") else BigDecimal("92859.94"),
                data.atOffset(ZoneOffset.UTC))
            return liquidacao
        }
        val inicioDia = dia.atStartOfDay().toInstant(ZoneOffset.UTC)
        val primeiro = inserir(orgA, cedenteA, "USD", inicioDia)
        val segundo = inserir(orgA, cedenteA, "USD", inicioDia)
        inserir(orgA, cedenteA, "BRL", inicioDia.plusSeconds(60))
        inserir(orgA, outroCedenteA, "USD", inicioDia.plusSeconds(120))
        val anterior = inserir(orgA, cedenteA, "USD", inicioDia.minusSeconds(1))
        val posterior = inserir(orgA, cedenteA, "USD", inicioDia.plusSeconds(86400))
        inserir(orgA, cedenteA, "BRL", inicioDia.plusSeconds(86399))
        inserir(orgB, cedenteB, "USD", inicioDia)
        val filtro = "inicio=$dia&fim=$dia&cedenteId=$cedenteA&moeda=USD&tamanho=1"
        val pagina0 = get("/api/v1/extrato?$filtro&pagina=0", tokenA)
        val pagina1 = get("/bff/v1/extrato?$filtro&pagina=1", tokenA)
        assertThat(pagina0.statusCode()).isEqualTo(200)
        assertThat(pagina1.statusCode()).isEqualTo(200)
        val ids = listOf(pagina0, pagina1).map { it.tree()["itens"][0]["liquidacaoId"].asText() }
        assertThat(ids).containsExactly(*listOf(primeiro.toString(), segundo.toString()).sortedDescending().toTypedArray())
        assertThat(pagina0.tree()["total"].asLong()).isEqualTo(2)
        assertThat(pagina1.tree()["total"].asLong()).isEqualTo(2)
        assertThat(get("/bff/v1/extrato?$filtro&pagina=0", tokenA).body()).isEqualTo(pagina0.body())
        val vazia = get("/api/v1/extrato?$filtro&pagina=2", tokenA).tree()
        assertThat(vazia["itens"]).isEmpty()
        assertThat(vazia["total"].asLong()).isEqualTo(2)
        assertThat(get("/api/v1/extrato?$filtro", tokenB).tree()["total"].asLong()).isZero()
        assertThat(get("/api/v1/extrato", tokenB).tree()["total"].asLong()).isEqualTo(1)
        assertThat(get("/api/v1/extrato?inicio=$dia&fim=$dia", tokenA).tree()["total"].asLong()).isEqualTo(5)
        val todos = get("/api/v1/extrato", tokenA).tree()["itens"]
        assertThat(todos.first()["liquidacaoId"].asText()).isEqualTo(posterior.toString())
        assertThat(todos.last()["liquidacaoId"].asText()).isEqualTo(anterior.toString())
        val brl = todos.find { it["moeda"].asText() == "BRL" }!!
        assertThat(brl["taxaBrlPorUnidade"].isNull).isTrue()
        assertThat(brl["cotacaoCambialId"].isNull).isTrue()
        val item = pagina0.tree()["itens"][0]
        assertThat(item["cedenteId"].asText()).isEqualTo(cedenteA.toString())
        assertThat(item["valorFaceBrl"].asText()).isEqualTo("100000.00")
        assertThat(item["valorPresenteBrl"].asText()).isEqualTo("92859.94")
        assertThat(item["desagioBrl"].asText()).isEqualTo("7140.06")
        assertThat(item["valorPagamento"].asText()).isEqualTo("17094.67")
        assertThat(item["cotacaoCambialId"].asText()).isEqualTo(cotacao.id.toString())
        assertThat(item["liquidadaEm"].asText()).isEqualTo(inicioDia.toString())
    }

    @Test
    fun `extrato rejeita filtros invalidos e exige autenticacao`() {
        listOf("inicio=invalido", "fim=2026-02-30", "inicio=2026-02-02&fim=2026-02-01",
            "cedenteId=123", "moeda=EUR", "pagina=-1", "pagina=999999999999", "tamanho=0", "tamanho=101")
            .forEach { assertThat(get("/api/v1/extrato?$it", tokenA).statusCode()).isEqualTo(400) }
        assertThat(get("/bff/v1/extrato").statusCode()).isEqualTo(401)
    }

    @Test
    fun `simulacao previa calcula golden cases nas duas fronteiras sem gravar e usa taxas vigentes`() {
        val instante = Instant.now().truncatedTo(ChronoUnit.MICROS)
        taxas.registrarTaxaBase(BigDecimal("0.01"), instante)
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), instante)
        taxas.registrarSpread(Modalidades.CHEQUE_PRE_DATADO, BigDecimal("0.025"), instante)
        taxas.registrarCotacao(BigDecimal("5.4321"), instante)
        taxas.registrarTaxaBase(BigDecimal("0.50"), instante.plusSeconds(86400))
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.50"), instante.plusSeconds(86400))
        taxas.registrarCotacao(BigDecimal("9.00"), instante.plusSeconds(86400))
        val tabelas = listOf("cedentes", "recebiveis", "liquidacoes", "idempotencia", "auditoria_liquidacoes",
            "taxas_base", "spreads_recebiveis", "cotacoes_cambiais")
        val antes = tabelas.associateWith { jdbc.queryForObject("SELECT count(*) FROM $it", Long::class.java) }
        val casos = listOf(
            Triple("""{"modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":"100000.00","prazoMeses":3,"moeda":"BRL"}""", "92859.94", "7140.06"),
            Triple("""{"modalidade":"CHEQUE_PRE_DATADO","valorFaceBrl":"25000.00","prazoMeses":2,"moeda":"BRL"}""", "23337.77", "1662.23"),
            Triple("""{"modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":"100000.00","prazoMeses":3,"moeda":"USD"}""", "17094.67", "7140.06"),
        )
        casos.forEach { (corpo, pagamento, desagio) ->
            val respostas = listOf("/api/v1", "/bff/v1").map { prefixo ->
                val resposta = post("$prefixo/simulacoes", tokenA, corpo)
                assertThat(resposta.statusCode()).isEqualTo(200)
                val dados = resposta.tree()
                assertThat(dados["valorPagamento"].isTextual).isTrue()
                assertThat(dados["valorPagamento"].asText()).isEqualTo(pagamento)
                assertThat(dados["desagioBrl"].asText()).isEqualTo(desagio)
                assertThat(BigDecimal(dados["taxaBaseMensal"].asText())).isEqualByComparingTo("0.01")
                assertThat(dados["referencia"].asText()).endsWith("Z")
                dados
            }
            listOf("moeda", "valorPresenteBrl", "desagioBrl", "valorPagamento", "taxaBaseMensal", "spreadMensal",
                "cotacaoBrlPorUsd", "cotacaoValidaAte").forEach { campo ->
                assertThat(respostas[0][campo]).isEqualTo(respostas[1][campo])
            }
            if (corpo.contains("USD")) {
                assertThat(respostas[0]["valorPresenteBrl"].asText()).isEqualTo("92859.94")
                assertThat(BigDecimal(respostas[0]["cotacaoBrlPorUsd"].asText())).isEqualByComparingTo("5.4321")
                assertThat(respostas[0]["cotacaoValidaAte"].asText()).endsWith("Z")
            } else {
                assertThat(respostas[0]["cotacaoBrlPorUsd"].isNull).isTrue()
                assertThat(respostas[0]["cotacaoValidaAte"].isNull).isTrue()
            }
        }
        assertThat(tabelas.associateWith { jdbc.queryForObject("SELECT count(*) FROM $it", Long::class.java) }).isEqualTo(antes)
        val novaVigencia = Instant.now().truncatedTo(ChronoUnit.MICROS)
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.02"), novaVigencia)
        val novaPrevia = post("/api/v1/simulacoes", tokenA, casos[0].first)
        assertThat(novaPrevia.statusCode()).isEqualTo(200)
        assertThat(BigDecimal(novaPrevia.tree()["spreadMensal"].asText())).isEqualByComparingTo("0.02")
        assertThat(novaPrevia.tree()["valorPagamento"].asText()).isNotEqualTo("92859.94")
    }

    @Test
    fun `simulacao exige autenticacao e rejeita dados invalidos e campos adicionais`() {
        val valido = """{"modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":"100.00","prazoMeses":1,"moeda":"BRL"}"""
        listOf("/api/v1", "/bff/v1").forEach { prefixo ->
            assertThat(post("$prefixo/simulacoes", null, valido).statusCode()).isEqualTo(401)
            listOf(
                valido.replace("\"100.00\"", "100.00"), valido.replace("100.00", "0"),
                valido.replace("100.00", "1.001"), valido.replace("100.00", "100000000000000000"),
                valido.replace("\"prazoMeses\":1", "\"prazoMeses\":0"),
                valido.replace("\"prazoMeses\":1", "\"prazoMeses\":1.0"),
                valido.replace("DUPLICATA_MERCANTIL", "OUTRA"), valido.replace("BRL", "EUR"),
                valido.dropLast(1) + ""","recebivelId":"${UUID.randomUUID()}"}""",
                valido.dropLast(1) + ""","organizacaoId":"$orgA"}""",
                valido.dropLast(1) + ""","chaveIdempotencia":"x"}""",
            ).forEach { assertThat(post("$prefixo/simulacoes", tokenA, it).statusCode()).isEqualTo(400) }
        }
    }

    @Test
    fun `simulacao rejeita dados ausentes e cambio vencido sem impedir BRL`() {
        fun servico(instante: Instant) = ServicoSimulacao(ManterEConsultarTaxas(
            HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), Clock.fixed(instante, ZoneOffset.UTC),
        ))
        val comando = ComandoSimulacao(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("100000.00"), 3, MoedaPagamento.USD)
        assertThrows<DadosFinanceirosIndisponiveis> { servico(Instant.parse("1900-01-01T00:00:00Z")).simular(comando) }
        val agora = Instant.now().truncatedTo(ChronoUnit.MICROS)
        taxas.registrarTaxaBase(BigDecimal("0.01"), agora)
        taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), agora)
        taxas.registrarCotacao(BigDecimal("5.4321"), agora)
        val vencida = servico(agora.plusSeconds(1801))
        assertThrows<DadosFinanceirosIndisponiveis> { vencida.simular(comando) }
        assertThat(vencida.simular(comando.copy(moeda = MoedaPagamento.BRL)).precificacao.valorPresenteBrl)
            .isEqualByComparingTo("92859.94")
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
        listOf("api", "bff").forEach { prefixo ->
            assertThat(doc["paths"]["/$prefixo/v1/extrato"]["get"]).isNotNull()
        }
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
            listOf(
                "/$prefixo/v1/simulacoes",
                "/$prefixo/v1/cedentes",
                "/$prefixo/v1/recebiveis",
                "/$prefixo/v1/recebiveis/{id}/liquidacoes",
            ).forEach { caminho ->
                assertThat(doc["paths"][caminho]["post"]["tags"].map { it.asText() }).containsExactly("contratos-http")
            }
            val simulacao = doc["paths"]["/$prefixo/v1/simulacoes"]["post"]
            assertThat(simulacao["requestBody"]["content"]["application/json"]["schema"]["\$ref"].asText())
                .endsWith("/SimulacaoEntrada")
            assertThat(simulacao["responses"]["200"]["content"]["application/json"]["schema"]["\$ref"].asText())
                .endsWith("/SimulacaoResposta")
            assertThat(simulacao["security"][0]["bearerAuth"].isArray).isTrue()
            assertThat(simulacao.path("parameters").toString()).doesNotContain("Idempotency-Key")
            listOf("cedentes" to "CedenteResposta", "recebiveis" to "RecebivelResposta").forEach { (rota, schema) ->
                assertThat(doc["paths"]["/$prefixo/v1/$rota"]["post"]["responses"]["201"]
                    ["content"]["application/json"]["schema"]["\$ref"].asText()).endsWith("/$schema")
            }
        }
        assertThat(doc["components"]["schemas"]["RecebivelEntrada"]["properties"]["valorFaceBrl"]["type"].asText())
            .isEqualTo("string")
        assertThat(doc["components"]["schemas"]["LiquidacaoResposta"]["properties"]["valorPagamento"]["type"].asText())
            .isEqualTo("string")
        assertThat(doc["components"]["schemas"]["SimulacaoEntrada"]["properties"]["valorFaceBrl"]["type"].asText())
            .isEqualTo("string")
        assertThat(doc["components"]["schemas"]["SimulacaoResposta"]["properties"]["valorPagamento"]["type"].asText())
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
