package br.com.srm.creditengine.lotes

import br.com.srm.creditengine.TestJwtSecret
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.lotes.adaptador.saida.LotesJdbc
import br.com.srm.creditengine.lotes.aplicacao.ConflitoImportacaoLote
import br.com.srm.creditengine.lotes.aplicacao.ItemImportacao
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
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

    @Test
    fun `CNPJ CSV e XLSX resolvem cedentes ativos e confirmacao revalida situacao`() {
        val numerico = novoCedente(org, "00000000000191")
        val alfa = novoCedente(org, "12ABC34501DE35")
        val a = previaDocumento("texto.csv", csvDocumento("00000000000191").toByteArray())
        val b = previaDocumento("texto.xlsx", xlsxDocumento("12.abc.345/01de-35"), "/bff/v1")
        assertThat(a["itens"][0]["comando"]["cedenteId"].asText()).isEqualTo(numerico.toString())
        assertThat(b["itens"][0]["comando"]["cedenteId"].asText()).isEqualTo(alfa.toString())
        val porUuid = previa(VETOR_CSV + "\nref;$alfa;DUPLICATA_MERCANTIL;100.00;3;;BRL")
        assertThat(comandos(b)).isEqualTo(comandos(porUuid))
        for (documento in listOf("00000000000191", "00.000.000/0001-91", "12ABC34501DE35", "12.abc.345/01de-35")) {
            val csv = previaDocumento("texto.csv", csvDocumento(documento).toByteArray(), "/bff/v1")
            val xlsx = previaDocumento("texto.xlsx", xlsxDocumento(documento))
            assertThat(comandos(csv)).isEqualTo(comandos(xlsx))
            assertThat(csv["confirmavel"].asBoolean()).isTrue()
        }
        semLiquidacao(0)
        jdbc.update("UPDATE cedentes SET ativo = false WHERE organizacao_id = ? AND id = ?", org, numerico)
        val rejeitada = http.send(confirmar("inativo", comandos(a)), HttpResponse.BodyHandlers.ofString())
        assertThat(rejeitada.statusCode()).isEqualTo(422)
        assertThat(contar("importacoes_recebiveis")).isZero()
        val confirmada = http.send(confirmar("alfa", comandos(b)), HttpResponse.BodyHandlers.ofString())
        assertThat(confirmada.statusCode()).isEqualTo(201)
        assertThat(http.send(confirmar("alfa", comandos(porUuid), "/bff/v1"), HttpResponse.BodyHandlers.ofString()).body())
            .isEqualTo(confirmada.body())
        val divergente = comandos(porUuid).replace("100.00", "101.00")
        assertThat(http.send(confirmar("alfa", divergente), HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(409)
        semLiquidacao(1)
    }

    @Test
    fun `CNPJ invalido ausente inativo legado e externo geram erro seguro por linha`() {
        val inativo = novoCedente(org, "90021382000122")
        jdbc.update("UPDATE cedentes SET ativo = false WHERE id = ?", inativo)
        val outra = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'Outra')", outra)
        novoCedente(outra, "90024778000123")
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'Legado', ?)",
            UUID.randomUUID(), org, "90025108000121")
        val documentos = listOf(
            "12ABC34501DE36", "", "90021382000122", "90024778000123", "90025108000121", "90025255000100",
            "00!000000000191", "０００００００００００１９１", "00000000000000", "0000000000191",
        )
        val arquivo = CABECALHO_DOCUMENTO + "\n" + documentos.mapIndexed { i, doc ->
            "ref$i;$doc;DUPLICATA_MERCANTIL;100.00;3;;BRL"
        }.joinToString("\n")
        val previa = previaDocumento("negativos.csv", arquivo.toByteArray())
        assertThat(previa["confirmavel"].asBoolean()).isFalse()
        assertThat(previa["itens"].map { it["status"].asText() }).containsOnly("INVALIDO")
        assertThat(previa["itens"].map { it["erros"][0]["campo"].asText() }).containsOnly("cedenteDocumento")
        assertThat(previa.toString()).doesNotContain(outra.toString(), "90024778000123")
        val multiplos = previaDocumento("erros.csv",
            (CABECALHO_DOCUMENTO + "\nref;12ABC34501DE36;DUPLICATA_MERCANTIL;invalido;3;;BRL").toByteArray())
        assertThat(multiplos["itens"][0]["erros"].map { it["campo"].asText() })
            .containsExactlyInAnyOrder("valorFaceBrl", "cedenteDocumento")
        val rejeitada = http.send(confirmar("invalidos", comandos(previa)), HttpResponse.BodyHandlers.ofString())
        assertThat(rejeitada.statusCode()).isEqualTo(422)
        semLiquidacao(0)
        assertThat(contar("importacoes_recebiveis")).isZero()
    }

    @Test
    fun `documento estrangeiro e reserva ambigua nao resolvem mas UUID continua compativel`() {
        val estrangeiro = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO cedentes (id, organizacao_id, nome, documento, pais, tipo_documento, documento_canonico)
               VALUES (?, ?, 'Estrangeiro', '90024779000178', 'US', 'OUTRO', '90024779000178')""",
            estrangeiro, org,
        )
        val legado = UUID.randomUUID()
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'Legado', ?)",
            legado, org, "90025108000121")
        val canonico = novoCedente(org, "00000000000191")
        // Simula reserva conservadora mantida por uma reconciliação operacional incompleta.
        jdbc.update("UPDATE cedentes_documentos_reservas SET legado_id = ? WHERE organizacao_id = ? AND chave = ?",
            legado, org, "00000000000191")
        for ((id, documento) in listOf(
            estrangeiro to "90024779000178", legado to "90025108000121", canonico to "00000000000191",
        )) {
            val negada = previaDocumento("documento.xlsx", xlsxDocumento(documento))
            assertThat(negada["confirmavel"].asBoolean()).isFalse()
            assertThat(negada["itens"][0]["erros"][0]["campo"].asText()).isEqualTo("cedenteDocumento")
            val uuid = previa(VETOR_CSV + "\nref-$id;$id;DUPLICATA_MERCANTIL;100.00;3;;BRL")
            assertThat(uuid["confirmavel"].asBoolean()).isTrue()
            assertThat(http.send(confirmar("uuid-$id", comandos(uuid)), HttpResponse.BodyHandlers.ofString()).statusCode())
                .isEqualTo(201)
        }
        semLiquidacao(3)
    }

    @Test
    fun `confirmacao JSON exige UUID canonico e recusa documento ou organizacao no corpo`() {
        val id = novoCedente(org, "00000000000191")
        val previa = previaDocumento("documento.csv", csvDocumento("00000000000191").toByteArray())
        val canonico = comandos(previa)
        val documento = canonico.replace("cedenteId", "cedenteDocumento").replace(id.toString(), "00000000000191")
        val organizacao = canonico.replace("\"cedenteId\":", "\"organizacaoId\":\"$org\",\"cedenteId\":")
        for (base in listOf("/api/v1", "/bff/v1")) {
            for (corpo in listOf(documento, organizacao)) {
                assertThat(http.send(confirmar("contrato", corpo, base), HttpResponse.BodyHandlers.ofString()).statusCode())
                    .isEqualTo(422)
            }
        }
        semLiquidacao(0)
        assertThat(contar("importacoes_recebiveis")).isZero()
    }

    @Test
    fun `lotes A B por CNPJ preservam concorrencia e quatro duplicidades`() {
        novoCedente(org, "00000000000191")
        val arquivos = listOf(fixture("SRM_LOTE_A_200_RECEBIVEIS.csv"), fixture("SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv"))
            .map { csv -> csv.replace("cedenteId", "cedenteDocumento").replace(cedente.toString(), "00000000000191") }
        val previas = arquivos.map { previaDocumento("lote.csv", it.toByteArray()) }
        assertThat(previas.map { it["confirmavel"].asBoolean() }).containsExactly(true, true)
        semLiquidacao(0)
        val corpos = previas.map(::comandos)
        val respostas = corpos.mapIndexed { i, corpo ->
            http.sendAsync(confirmar("cnpj-$i", corpo), HttpResponse.BodyHandlers.ofString())
        }.map { it.get(40, java.util.concurrent.TimeUnit.SECONDS) }
        assertThat(respostas.map { it.statusCode() }).containsExactlyInAnyOrder(201, 409)
        val vencedor = respostas.indexOfFirst { it.statusCode() == 201 }
        val replay = http.send(confirmar("cnpj-$vencedor", corpos[vencedor]), HttpResponse.BodyHandlers.ofString())
        assertThat(replay.statusCode()).isEqualTo(201)
        assertThat(replay.body()).isEqualTo(respostas[vencedor].body())
        val perdedor = previaDocumento("lote.csv", arquivos[1 - vencedor].toByteArray())
        assertThat(perdedor["itens"].count { it["status"].asText() == "JA_CADASTRADO" }).isEqualTo(4)
        assertThat(perdedor["itens"].count { it["status"].asText() == "NOVO" }).isEqualTo(196)
        semLiquidacao(200)
        assertThat(contar("importacoes_recebiveis")).isEqualTo(1)

        val taxas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), Clock.systemUTC())
        val agora = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MICROS)
        taxas.registrarTaxaBase(BigDecimal("0.01"), agora)
        taxas.registrarSpread("DUPLICATA_MERCANTIL", BigDecimal("0.015"), agora)
        taxas.registrarSpread("CHEQUE_PRE_DATADO", BigDecimal("0.025"), agora)
        taxas.registrarCotacao(BigDecimal("5.4321"), agora)
        val cadastro = json.readTree(respostas[vencedor].body())
        val moedas = previas[vencedor]["itens"].associate {
            it["comando"]["referenciaExterna"].asText() to it["comando"]["moedaPagamento"].asText()
        }
        val itens = cadastro["itens"].map {
            mapOf("recebivelId" to it["recebivelId"].asText(),
                "moedaPagamento" to moedas.getValue(it["referenciaExterna"].asText()))
        }
        val lote = cadastro["loteId"].asText()
        val pedidoPrevia = post("/api/v1/lotes/$lote/previas-liquidacao",
            json.writeValueAsString(mapOf("itens" to itens)), "application/json")
            .header("Authorization", "Bearer $token").build()
        val financeira = http.send(pedidoPrevia, HttpResponse.BodyHandlers.ofString())
        assertThat(financeira.statusCode()).isEqualTo(200)
        val previaFinanceira = json.readTree(financeira.body())
        assertThat(previaFinanceira["aptoParaLiquidar"].asBoolean()).isTrue()
        val corpoFinanceiro = json.writeValueAsString(mapOf(
            "itens" to itens, "hashPrevia" to previaFinanceira["hashPrevia"].asText(), "confirmacaoFinanceira" to true,
        ))
        val liquidar = post("/api/v1/lotes/$lote/liquidacoes", corpoFinanceiro, "application/json")
            .header("Authorization", "Bearer $token").header("Idempotency-Key", "financeira-cnpj").build()
        assertThat(http.send(liquidar, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201)
        assertThat(contar("liquidacoes")).isEqualTo(200)
        val aposLiquidacao = previaDocumento("lote.csv", arquivos[1 - vencedor].toByteArray())
        assertThat(aposLiquidacao["itens"].count { it["status"].asText() == "JA_LIQUIDADO" }).isEqualTo(4)
        assertThat(aposLiquidacao["itens"].count { it["status"].asText() == "NOVO" }).isEqualTo(196)
    }

    @Test
    fun `mesmo CNPJ em organizacoes distintas resolve somente o cedente local e referencia continua unica`() {
        val local = novoCedente(org, "90024779000178")
        val outra = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'Outra')", outra)
        val alheio = novoCedente(outra, "90024779000178")
        val primeiro = previaDocumento("local.csv", csvDocumento("90024779000178").toByteArray())
        assertThat(primeiro["itens"][0]["comando"]["cedenteId"].asText()).isEqualTo(local.toString())
        assertThat(primeiro.toString()).doesNotContain(alheio.toString(), outra.toString())
        assertThat(http.send(confirmar("ref-local", comandos(primeiro)), HttpResponse.BodyHandlers.ofString()).statusCode())
            .isEqualTo(201)
        novoCedente(org, "90024780000100")
        val duplicado = previaDocumento("outro.csv", csvDocumento("90024780000100").toByteArray())
        assertThat(duplicado["itens"][0]["status"].asText()).isEqualTo("JA_CADASTRADO")
        assertThat(http.send(confirmar("outro-cedente", comandos(duplicado)), HttpResponse.BodyHandlers.ofString()).statusCode())
            .isEqualTo(409)
        semLiquidacao(1)
    }

    @Test
    fun `confirmacoes concorrentes de comando resolvido disputam mesma referencia na transacao`() {
        val id = novoCedente(org, "04740714000197")
        val previa = previaDocumento("concorrente.csv", csvDocumento("04740714000197").toByteArray())
        assertThat(previa["itens"][0]["comando"]["cedenteId"].asText()).isEqualTo(id.toString())
        val item = ItemImportacao("ref", id, "DUPLICATA_MERCANTIL", BigDecimal("100.00"), 3, null, MoedaPagamento.BRL)
        val lotes = LotesJdbc(jdbc, TransactionTemplate(gerenciador))
        val barreira = CyclicBarrier(2)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val resultados = (1..2).map { numero ->
                executor.submit<Result<*>> {
                    runCatching {
                        lotes.confirmar(IdentidadeUsuario(autor, org), "disputa-$numero", "c".repeat(64), listOf(item)) {
                            barreira.await(20, TimeUnit.SECONDS)
                        }
                    }
                }
            }.map { it.get(30, TimeUnit.SECONDS) }
            assertThat(resultados.count { it.isSuccess }).isEqualTo(1)
            assertThat(resultados.single { it.isFailure }.exceptionOrNull()).isInstanceOf(ConflitoImportacaoLote::class.java)
            assertThat(contar("importacoes_recebiveis")).isEqualTo(1)
            assertThat(contar("importacoes_recebiveis_itens")).isEqualTo(1)
            semLiquidacao(1)
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `inativacao concorrente prevalece antes da revalidacao transacional`() {
        val id = novoCedente(org, "44108058000129")
        val previa = previaDocumento("cedente.csv", csvDocumento("44108058000129").toByteArray())
        val alterado = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val transacao = executor.submit {
                TransactionTemplate(gerenciador).execute {
                    jdbc.update("UPDATE cedentes SET ativo = false WHERE organizacao_id = ? AND id = ?", org, id)
                    alterado.countDown()
                    check(liberar.await(20, TimeUnit.SECONDS))
                }
            }
            check(alterado.await(10, TimeUnit.SECONDS))
            val resposta = http.sendAsync(confirmar("inativacao-concorrente", comandos(previa)), HttpResponse.BodyHandlers.ofString())
            val prazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var bloqueada = false
            while (!bloqueada && System.nanoTime() < prazo) {
                bloqueada = jdbc.queryForObject(
                    """SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE wait_event_type = 'Lock'
                       AND query LIKE '%SELECT id FROM cedentes WHERE organizacao_id = % FOR SHARE%')""",
                    Boolean::class.java,
                ) == true
                if (!bloqueada) Thread.sleep(25)
            }
            assertThat(bloqueada).isTrue()
            liberar.countDown()
            transacao.get(10, TimeUnit.SECONDS)
            assertThat(resposta.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(422)
            assertThat(contar("importacoes_recebiveis")).isZero()
            semLiquidacao(0)
        } finally {
            liberar.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `mesma chave concorrente de comando resolvido retorna replay sem inserir novamente`() {
        val id = novoCedente(org, "90024420000109")
        val previa = previaDocumento("cedente.csv", csvDocumento("90024420000109").toByteArray())
        val corpo = comandos(previa)
        val bloqueio = CountDownLatch(1)
        val liberar = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val transacao = executor.submit {
                TransactionTemplate(gerenciador).execute {
                    jdbc.queryForObject("SELECT id FROM cedentes WHERE organizacao_id = ? AND id = ? FOR UPDATE",
                        UUID::class.java, org, id)
                    bloqueio.countDown()
                    check(liberar.await(20, TimeUnit.SECONDS))
                }
            }
            check(bloqueio.await(10, TimeUnit.SECONDS))
            val primeiro = http.sendAsync(confirmar("mesma-chave", corpo), HttpResponse.BodyHandlers.ofString())
            val prazo = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var aguardando = false
            while (!aguardando && System.nanoTime() < prazo) {
                aguardando = jdbc.queryForObject(
                    """SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE wait_event_type = 'Lock'
                       AND query LIKE '%SELECT id FROM cedentes WHERE organizacao_id = % FOR SHARE%')""",
                    Boolean::class.java,
                ) == true
                if (!aguardando) Thread.sleep(25)
            }
            assertThat(aguardando).isTrue()
            val segundo = http.sendAsync(confirmar("mesma-chave", corpo), HttpResponse.BodyHandlers.ofString())
            val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            var disputa = false
            while (!disputa && System.nanoTime() < limite) {
                disputa = jdbc.queryForObject(
                    """SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE wait_event_type = 'Lock'
                       AND query LIKE '%INSERT INTO importacoes_recebiveis %')""",
                    Boolean::class.java,
                ) == true
                if (!disputa) Thread.sleep(25)
            }
            assertThat(disputa).isTrue()
            liberar.countDown()
            transacao.get(10, TimeUnit.SECONDS)
            val a = primeiro.get(20, TimeUnit.SECONDS)
            val b = segundo.get(20, TimeUnit.SECONDS)
            assertThat(a.statusCode()).isEqualTo(201)
            assertThat(b.statusCode()).isEqualTo(201)
            assertThat(a.body()).isEqualTo(b.body())
            assertThat(contar("importacoes_recebiveis")).isEqualTo(1)
            assertThat(contar("importacoes_recebiveis_itens")).isEqualTo(1)
            semLiquidacao(1)
        } finally {
            liberar.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    private fun novoCedente(org: UUID, cnpj: String): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO cedentes (id, organizacao_id, nome, documento, pais, tipo_documento, documento_canonico)
               VALUES (?, ?, 'CNPJ', ?, 'BR', 'CNPJ', ?)""", id, org, cnpj, cnpj,
        )
        return id
    }

    private fun csvDocumento(documento: String) =
        "$CABECALHO_DOCUMENTO\nref;$documento;DUPLICATA_MERCANTIL;100.00;3;;BRL"

    private fun xlsxDocumento(documento: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun parte(nome: String, texto: String) {
                zip.putNextEntry(ZipEntry(nome))
                zip.write(texto.toByteArray())
                zip.closeEntry()
            }
            parte("[Content_Types].xml", "<Types/>")
            parte("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet r:id="rId1"/></sheets></workbook>""")
            parte("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Type="test/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""")
            val linhas = csvDocumento(documento).lines().mapIndexed { i, linha ->
                val numero = i + 1
                "<row r=\"$numero\">" + linha.split(';').mapIndexed { j, valor ->
                    "<c r=\"${'A' + j}$numero\" t=\"inlineStr\"><is><t>$valor</t></is></c>"
                }.joinToString("") + "</row>"
            }.joinToString("")
            parte("xl/worksheets/sheet1.xml", "<worksheet><sheetData>$linhas</sheetData></worksheet>")
        }
        return bytes.toByteArray()
    }

    private fun previaDocumento(nome: String, conteudo: ByteArray, base: String = "/api/v1"): JsonNode {
        val fronteira = "lote-${UUID.randomUUID()}"
        val inicio = "--$fronteira\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"$nome\"\r\n" +
            "Content-Type: application/octet-stream\r\n\r\n"
        val corpo = inicio.toByteArray() + conteudo + "\r\n--$fronteira--\r\n".toByteArray()
        val pedido = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$porta$base/lotes/recebiveis/validacoes"))
            .timeout(Duration.ofSeconds(30)).header("Content-Type", "multipart/form-data; boundary=$fronteira")
            .header("Authorization", "Bearer $token").POST(HttpRequest.BodyPublishers.ofByteArray(corpo)).build()
        val resposta = http.send(pedido, HttpResponse.BodyHandlers.ofString())
        assertThat(resposta.statusCode()).isEqualTo(200)
        return json.readTree(resposta.body())
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
        private const val CABECALHO_DOCUMENTO = "referenciaExterna;cedenteDocumento;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento"

        @JvmStatic
        @DynamicPropertySource
        fun jwt(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { TestJwtSecret.value }
        }
    }
}
