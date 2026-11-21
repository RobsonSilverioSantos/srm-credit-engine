package br.com.srm.creditengine.cadastro

import br.com.srm.creditengine.cadastro.adaptador.saida.CedentesJdbc
import br.com.srm.creditengine.cadastro.dominio.Cedente
import br.com.srm.creditengine.cadastro.dominio.DocumentoEmpresarial
import br.com.srm.creditengine.cadastro.dominio.chaveComparacaoDocumento
import java.sql.SQLException
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
class DocumentosPostgresTest {
    private val url = requireNotNull(System.getenv("SPRING_DATASOURCE_URL"))
    private val usuario = requireNotNull(System.getenv("SPRING_DATASOURCE_USERNAME"))
    private val senha = requireNotNull(System.getenv("SPRING_DATASOURCE_PASSWORD"))
    private val raiz = JdbcTemplate(DriverManagerDataSource(url, usuario, senha))
    private lateinit var schema: String
    private lateinit var datasource: DriverManagerDataSource
    private lateinit var jdbc: JdbcTemplate
    private lateinit var org: UUID

    @BeforeEach
    fun preparar() {
        schema = "b14_" + UUID.randomUUID().toString().replace("-", "")
        datasource = DriverManagerDataSource(url + (if ('?' in url) "&" else "?") + "currentSchema=$schema", usuario, senha)
        migracao("4").migrate()
        jdbc = JdbcTemplate(datasource)
        org = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'Teste B14')", org)
    }

    @AfterEach
    fun limpar() {
        raiz.execute("DROP SCHEMA IF EXISTS $schema CASCADE")
    }

    @Test
    fun `PostgreSQL de teste nao registra statements parametros nem detalhes com PII`() {
        mapOf(
            "log_statement" to "none",
            "log_min_error_statement" to "panic",
            "log_error_verbosity" to "terse",
            "log_parameter_max_length" to "0",
            "log_parameter_max_length_on_error" to "0",
        ).forEach { (parametro, esperado) ->
            assertThat(jdbc.queryForObject("SELECT current_setting(?)", String::class.java, parametro)).isEqualTo(esperado)
        }
    }

    @Test
    fun `migration preserva documento desconhecido IDs datas ativo e FK recebivel`() {
        val id = inserirLegado("nao-e-cnpj!")
        val recebivel = UUID.randomUUID()
        jdbc.update("""INSERT INTO recebiveis
            (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
            VALUES (?, ?, ?, 'DUPLICATA_MERCANTIL', 100, 1)""", recebivel, org, id)
        jdbc.update("UPDATE cedentes SET ativo = false WHERE id = ?", id)
        val antes = jdbc.queryForMap("SELECT * FROM cedentes WHERE id = ?", id)
        migracao().migrate()
        val depois = jdbc.queryForMap("SELECT * FROM cedentes WHERE id = ?", id)
        antes.forEach { (coluna, valor) -> assertThat(depois[coluna]).isEqualTo(valor) }
        assertThat(depois["pais"]).isNull()
        assertThat(depois["tipo_documento"]).isNull()
        assertThat(depois["documento_canonico"]).isNull()
        val cedente = CedentesJdbc(jdbc).buscar(org, id)!!
        assertThat(cedente.identificacao).isNull()
        assertThat(cedente.documento).isEqualTo("nao-e-cnpj!")
        assertThat(cedente.ativo).isFalse()
        assertThat(jdbc.queryForObject("SELECT cedente_id FROM recebiveis WHERE id = ?", UUID::class.java, recebivel))
            .isEqualTo(id)
        migracao().validate()
    }

    @Test
    fun `inventario falha atomicamente por colisao do legado sem exibir documentos`() {
        val documentos = listOf(" 12.abc.345/01DE-35 ", "12ABC34501DE35")
        documentos.forEach { inserirLegado(it) }
        val erro = assertThrows<FlywayException> { migracao().migrate() }
        val mensagens = generateSequence<Throwable>(erro) { it.cause }.map { it.message.orEmpty() }.joinToString("\n")
        assertThat(mensagens).contains("1 grupo(s)", "reconciliacao manual")
        documentos.forEach { assertThat(mensagens).doesNotContain(it) }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cedentes", Long::class.java)).isEqualTo(2)
        assertThat(jdbc.queryForObject("""SELECT count(*) FROM information_schema.columns
            WHERE table_schema = ? AND table_name = 'cedentes' AND column_name = 'pais'""", Long::class.java, schema)).isZero()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '5'", Long::class.java)).isZero()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname=? AND p.proname='srm_chave_documento'", Long::class.java, schema)).isZero()
    }

    @Test
    fun `colisoes historicas sao por tenant e casos sem ASCII nao sao reclassificados`() {
        inserirLegado("12.ABC.345/01DE-35")
        val outro = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'Outro')", outro)
        inserirLegado("12ABC34501DE35", outro)
        inserirLegado("ÁKſß")
        migracao().migrate()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cedentes WHERE pais IS NULL", Long::class.java)).isEqualTo(3)
    }

    @Test
    fun `mesma comparacao Kotlin SQL e validacao DV no banco nao aceitam malformed`() {
        migracao().migrate()
        (VetoresDocumento.oficiais + VetoresDocumento.malformados.filterNot { '\u0000' in it } +
            listOf("   a-b.c/1\t2\n", "ÁKＡſß а", "ß12abc!34501de35")).forEach { entrada ->
            assertThat(jdbc.queryForObject("SELECT srm_chave_documento(?)", String::class.java, entrada))
                .isEqualTo(chaveComparacaoDocumento(entrada))
        }
        assertThat(jdbc.queryForList("SELECT codigo FROM paises_documento", String::class.java))
            .containsExactlyInAnyOrderElementsOf(Locale.getISOCountries().toList())
        VetoresDocumento.oficiais.forEach { numero ->
            assertThat(jdbc.queryForObject("SELECT srm_cnpj_valido(?)", Boolean::class.java, numero)).isTrue()
            (12..13).forEach { pos ->
                ('0'..'9').filter { it != numero[pos] }.forEach { dv ->
                    val invalido = numero.toCharArray().also { it[pos] = dv }.concatToString()
                    assertThat(jdbc.queryForObject("SELECT srm_cnpj_valido(?)", Boolean::class.java, invalido)).isFalse()
                }
            }
        }
        (VetoresDocumento.malformados.filterNot { '\u0000' in it } + ('0'..'9').map { it.toString().repeat(14) }).forEach { numero ->
            assertThat(jdbc.queryForObject("SELECT srm_cnpj_valido(?)", Boolean::class.java, numero)).isFalse()
        }
        // PostgreSQL TEXT não representa NUL: rejeita no transporte antes da função.
        val nul = assertThrows<DataIntegrityViolationException> {
            jdbc.queryForObject("SELECT srm_cnpj_valido(?)", Boolean::class.java, "12ABC34501DE\u000035")
        }
        assertThat(sqlState(nul)).isEqualTo("22021")
        repeat(200) { indice ->
            val numero = VetoresDocumento.sintetico(indice.toString().padStart(12, '0').replaceFirst('0', 'Z'))
            assertThat(jdbc.queryForObject("SELECT srm_cnpj_valido(?)", Boolean::class.java, numero)).isTrue()
        }
    }

    @Test
    fun `banco impoe sintaxe DV ISO campos coesos e identidade imutavel`() {
        migracao().migrate()
        val invalidos = listOf(
            Triple("BR", "CNPJ", "12ABC34501DE36"), Triple("BR", "CNPJ", "00000000000000"),
            Triple("BR", "CNPJ", "12abc34501de35"), Triple("BR", "CNPJ", "12.ABC.345/01DE-35"),
            Triple("US", "CNPJ", "12ABC34501DE35"), Triple("US", "EIN", "123456789"),
            Triple("BR", "OUTRO", "123"),
            Triple("ZZ", "OUTRO", "ABC"), Triple("us", "OUTRO", "ABC"), Triple("US", "OUTRO", "A-B"),
            Triple("US", "OUTRO", "abc"), Triple("US", "OUTRO", "Á"), Triple("US", "OUTRO", "A\n"),
        )
        invalidos.forEach { (pais, tipo, numero) ->
            assertThatExceptionOfType(DataIntegrityViolationException::class.java).isThrownBy {
                inserirConhecido(pais, tipo, numero)
            }
        }
        assertThatExceptionOfType(DataIntegrityViolationException::class.java).isThrownBy {
            jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento, pais) VALUES (?, ?, 'Teste', 'A', 'US')",
                UUID.randomUUID(), org)
        }
        val id = inserirConhecido("US", "OUTRO", "ABC")
        assertThatExceptionOfType(DataIntegrityViolationException::class.java).isThrownBy {
            jdbc.update("UPDATE cedentes SET documento = 'DEF', documento_canonico = 'DEF' WHERE id = ?", id)
        }
        assertThat(jdbc.queryForObject("SELECT documento FROM cedentes WHERE id = ?", String::class.java, id)).isEqualTo("ABC")
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cedentes_documentos_reservas", Long::class.java)).isEqualTo(1)
    }

    @Test
    fun `novas coincidencias com legado sao bloqueadas sem pais tipo inferidos nem unir dados`() {
        val id = inserirLegado(" 12.abc.345/01DE-35 ")
        migracao().migrate()
        listOf("BR" to "CNPJ", "CA" to "OUTRO", "US" to "OUTRO").forEach { (pais, tipo) ->
            assertThatExceptionOfType(DataIntegrityViolationException::class.java).isThrownBy {
                inserirConhecido(pais, tipo, "12ABC34501DE35")
            }
        }
        assertThat(CedentesJdbc(jdbc).buscar(org, id)!!.identificacao).isNull()
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cedentes", Long::class.java)).isEqualTo(1)
        val outro = UUID.randomUUID()
        jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'Outro')", outro)
        inserirConhecido("BR", "CNPJ", "12ABC34501DE35", outro)
        jdbc.update("DELETE FROM cedentes WHERE id = ?", id)
        // Exclusão não equivale a reconciliação: reserva conservadora continua bloqueando.
        assertThatExceptionOfType(DataIntegrityViolationException::class.java).isThrownBy {
            inserirConhecido("US", "OUTRO", "12ABC34501DE35")
        }
    }

    @Test
    fun `numero igual em identidades conhecidas distintas nao colide no indice antigo`() {
        migracao().migrate()
        listOf("BR" to "CNPJ", "CA" to "OUTRO", "US" to "OUTRO").forEach { (pais, tipo) ->
            inserirConhecido(pais, tipo, "12ABC34501DE35")
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cedentes", Long::class.java)).isEqualTo(3)
        assertThatExceptionOfType(DataIntegrityViolationException::class.java).isThrownBy {
            inserirLegado("12.ABC.345/01DE-35")
        }
    }

    @Test
    fun `oito conexoes concorrentes canonicas tem exatamente um sucesso e sete conflitos`() {
        migracao().migrate()
        val executor = Executors.newFixedThreadPool(8)
        val prontos = CountDownLatch(8)
        val iniciar = CountDownLatch(1)
        try {
            val futuros = (0..7).map { indice ->
                executor.submit<Int> {
                    prontos.countDown()
                    check(iniciar.await(10, TimeUnit.SECONDS))
                    val entrada = if (indice % 2 == 0) "12.abc.345/01de-35" else "12ABC34501DE35"
                    try {
                        val documento = DocumentoEmpresarial.validar("BR", "CNPJ", entrada)
                        CedentesJdbc(jdbc).cadastrar(Cedente(UUID.randomUUID(), org, "Teste", documento.numero, identificacao = documento))
                        201
                    } catch (ex: DataIntegrityViolationException) {
                        assertThat(sqlState(ex)).isEqualTo("23505")
                        409
                    }
                }
            }
            assertThat(prontos.await(10, TimeUnit.SECONDS)).isTrue()
            iniciar.countDown()
            assertThat(futuros.map { it.get(15, TimeUnit.SECONDS) }.sorted())
                .containsExactly(201, 409, 409, 409, 409, 409, 409, 409)
            assertThat(jdbc.queryForObject("SELECT count(*) FROM cedentes", Long::class.java)).isEqualTo(1)
        } finally {
            iniciar.countDown()
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `reserva aguarda legado nao commitado e rejeita apos commit ou permite apos rollback`() {
        migracao().migrate()
        listOf(true, false).forEach { commit ->
            val documento = if (commit) "12ABC34501DE35" else "ABCDEFGHIJKL80"
            datasource.connection.use { legado ->
                legado.autoCommit = false
                legado.prepareStatement("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'Legado', ?)").use {
                    it.setObject(1, UUID.randomUUID()); it.setObject(2, org); it.setString(3, VetoresDocumento.comMascara(documento))
                    it.executeUpdate()
                }
                val executor = Executors.newSingleThreadExecutor()
                val conectado = CountDownLatch(1)
                var pid = 0
                try {
                    val futuro = executor.submit<String> {
                        datasource.connection.use { nova ->
                            nova.createStatement().use { st ->
                                st.execute("SET statement_timeout = '10s'")
                                st.executeQuery("SELECT pg_backend_pid()").use { rs -> rs.next(); pid = rs.getInt(1) }
                            }
                            conectado.countDown()
                            try {
                                nova.prepareStatement("""INSERT INTO cedentes
                                    (id, organizacao_id, nome, documento, pais, tipo_documento, documento_canonico)
                                    VALUES (?, ?, 'Novo', ?, 'BR', 'CNPJ', ?)""").use {
                                    it.setObject(1, UUID.randomUUID()); it.setObject(2, org)
                                    it.setString(3, documento); it.setString(4, documento); it.executeUpdate()
                                }
                                "201"
                            } catch (ex: SQLException) {
                                ex.sqlState
                            }
                        }
                    }
                    assertThat(conectado.await(5, TimeUnit.SECONDS)).isTrue()
                    val limite = System.nanoTime() + Duration.ofSeconds(5).toNanos()
                    var esperando = false
                    while (!esperando && System.nanoTime() < limite) {
                        esperando = raiz.queryForObject("SELECT wait_event_type = 'Lock' FROM pg_stat_activity WHERE pid = ?", Boolean::class.java, pid) == true
                        if (!esperando) Thread.sleep(20)
                    }
                    assertThat(esperando).describedAs("Escritor deve esperar reserva na outra conexão PostgreSQL").isTrue()
                    if (commit) legado.commit() else legado.rollback()
                    assertThat(futuro.get(10, TimeUnit.SECONDS)).isEqualTo(if (commit) "23505" else "201")
                } finally {
                    legado.rollback()
                    executor.shutdownNow()
                    check(executor.awaitTermination(10, TimeUnit.SECONDS))
                }
            }
        }
    }

    private fun migracao(target: String? = null): Flyway {
        val configuracao = Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration")
        if (target != null) configuracao.target(MigrationVersion.fromVersion(target))
        return configuracao.load()
    }

    private fun inserirLegado(documento: String, tenant: UUID = org): UUID = UUID.randomUUID().also {
        jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'Legado', ?)", it, tenant, documento)
    }

    private fun inserirConhecido(pais: String, tipo: String, documento: String, tenant: UUID = org): UUID = UUID.randomUUID().also {
        jdbc.update("""INSERT INTO cedentes (id, organizacao_id, nome, documento, pais, tipo_documento, documento_canonico)
            VALUES (?, ?, 'Conhecido', ?, ?, ?, ?)""", it, tenant, documento, pais, tipo, documento)
    }

    private fun sqlState(ex: Throwable): String? =
        generateSequence(ex) { it.cause }.filterIsInstance<SQLException>().firstOrNull()?.sqlState
}
