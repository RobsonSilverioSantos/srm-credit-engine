package br.com.srm.creditengine.liquidacao

import br.com.srm.creditengine.cadastro.adaptador.saida.RecebiveisJdbc
import br.com.srm.creditengine.liquidacao.adaptador.saida.LiquidacaoJdbc
import br.com.srm.creditengine.liquidacao.aplicacao.ServicoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ComandoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.TransacaoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ResultadoOperacaoLiquidacao
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.taxas.adaptador.saida.HistoricoFinanceiroJdbc
import br.com.srm.creditengine.taxas.aplicacao.ManterEConsultarTaxas
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.dominio.SeletorTaxas
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.sql.DataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@EnabledIfEnvironmentVariable(named = "SRM_TEST_POSTGRES", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class IntegridadeB07PostgresTest {
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var manager: PlatformTransactionManager

    private inner class Fixture {
        val instante = Instant.parse("2080-01-01T00:00:00Z").plusSeconds(sequencia.incrementAndGet() * 86400L)
        val clock = Clock.fixed(instante, ZoneOffset.UTC)
        val org = UUID.randomUUID()
        val usuario = UUID.randomUUID()
        val cedente = UUID.randomUUID()
        val taxas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), clock)
        val repositorio = LiquidacaoJdbc(jdbc, TransactionTemplate(manager), clock)

        init {
            jdbc.update("INSERT INTO organizacoes (id, nome) VALUES (?, 'B07')", org)
            jdbc.update("INSERT INTO usuarios (id, organizacao_id, email, senha_hash) VALUES (?, ?, ?, 'hash')",
                usuario, org, "$usuario@example.invalid")
            jdbc.update("INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, 'B07', 'doc')",
                cedente, org)
            taxas.registrarTaxaBase(BigDecimal("0.01"), instante)
            taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.015"), instante)
            taxas.registrarSpread(Modalidades.CHEQUE_PRE_DATADO, BigDecimal("0.025"), instante)
            taxas.registrarCotacao(BigDecimal("5.4321"), instante)
        }

        fun recebivel(modalidade: String = Modalidades.DUPLICATA_MERCANTIL, face: String = "100000.00", prazo: Int = 3): UUID {
            val id = UUID.randomUUID()
            jdbc.update("""INSERT INTO recebiveis
                (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses)
                VALUES (?, ?, ?, ?, ?, ?)""", id, org, cedente, modalidade, BigDecimal(face), prazo)
            return id
        }

        fun servico(transacao: TransacaoLiquidacao = repositorio, dados: TaxasPorta = taxas) =
            ServicoLiquidacao(IdentidadeConfiavel { IdentidadeLiquidacao(org, usuario) },
                RecebiveisJdbc(jdbc), repositorio, dados, transacao, clock)

        fun contar(tabela: String): Int =
            jdbc.queryForObject("SELECT count(*) FROM $tabela WHERE organizacao_id = ?", Int::class.java, org)!!

        fun semEfeitos(id: UUID) {
            val estado = jdbc.queryForMap("SELECT status, versao FROM recebiveis WHERE id = ? AND organizacao_id = ?", id, org)
            assertThat(estado["status"]).isEqualTo("DISPONIVEL")
            assertThat(estado["versao"]).isEqualTo(0L)
            listOf("liquidacoes", "idempotencia", "auditoria_liquidacoes").forEach { assertThat(contar(it)).isZero() }
        }
    }

    private fun <T> simultaneos(tarefas: List<() -> T>): List<T> {
        val executor = Executors.newFixedThreadPool(tarefas.size)
        try {
            return tarefas.map { tarefa -> executor.submit<T> { tarefa() } }.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
            check(executor.awaitTermination(10, TimeUnit.SECONDS)) { "Threads de teste não encerraram" }
        }
    }

    @RepeatedTest(5)
    fun `duas versoes iguais e chaves diferentes produzem somente um commit`() {
        val f = Fixture()
        val id = f.recebivel()
        val barreira = CountDownLatch(2)
        val versoes = ConcurrentLinkedQueue<Long>()
        val transacao = TransacaoLiquidacao { identidade, chave, hash, liquidacao ->
            versoes.add(liquidacao.recebivel.versao)
            barreira.countDown()
            check(barreira.await(10, TimeUnit.SECONDS))
            f.repositorio.confirmar(identidade, chave, hash, liquidacao)
        }
        val resultados = simultaneos(listOf("a", "b").map { chave ->
            { runCatching { chave to f.servico(transacao).liquidar(ComandoLiquidacao(id, MoedaPagamento.BRL, chave)) } }
        })
        assertThat(versoes).containsExactlyInAnyOrder(0L, 0L)
        assertThat(resultados.count { it.isSuccess }).isEqualTo(1)
        assertThat(resultados.single { it.isFailure }.exceptionOrNull()).isInstanceOf(ConflitoLiquidacao::class.java)
        val vencedor = resultados.single { it.isSuccess }.getOrThrow()
        val estado = jdbc.queryForMap("SELECT status, versao FROM recebiveis WHERE id = ?", id)
        assertThat(estado["status"]).isEqualTo("LIQUIDADO")
        assertThat(estado["versao"]).isEqualTo(1L)
        assertThat(jdbc.queryForObject("SELECT versao_recebivel FROM liquidacoes WHERE id = ?", Long::class.java, vencedor.second.liquidacao.liquidacaoId)).isEqualTo(1L)
        assertThat(jdbc.queryForObject("SELECT chave FROM idempotencia WHERE organizacao_id = ? AND situacao = 'CONCLUIDA' AND liquidacao_id = ?",
            String::class.java, f.org, vencedor.second.liquidacao.liquidacaoId)).isEqualTo(vencedor.first)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_liquidacoes WHERE organizacao_id = ? AND liquidacao_id = ?",
            Int::class.java, f.org, vencedor.second.liquidacao.liquidacaoId)).isEqualTo(1)
        listOf("liquidacoes", "idempotencia", "auditoria_liquidacoes").forEach { assertThat(f.contar(it)).isEqualTo(1) }
    }

    @RepeatedTest(3)
    fun `mesma intencao concorrente e replay conservam resposta e nao recalculam no replay`() {
        val f = Fixture()
        val id = f.recebivel()
        val comando = ComandoLiquidacao(id, MoedaPagamento.USD, "mesma")
        val barreira = CountDownLatch(2)
        val transacao = TransacaoLiquidacao { identidade, chave, hash, liquidacao ->
            barreira.countDown()
            check(barreira.await(10, TimeUnit.SECONDS))
            f.repositorio.confirmar(identidade, chave, hash, liquidacao)
        }
        val resultados = simultaneos((1..2).map { { f.servico(transacao).liquidar(comando) } })
        val primeiro = resultados.first()
        assertThat(resultados).containsOnly(primeiro)
        val consultas = AtomicInteger()
        val taxasObservadas = object : TaxasPorta by f.taxas {
            override fun consultarEm(modalidade: String, exigirCotacao: Boolean, referencia: Instant) =
                f.taxas.consultarEm(modalidade, exigirCotacao, referencia).also { consultas.incrementAndGet() }
        }
        repeat(3) { assertThat(f.servico(dados = taxasObservadas).liquidar(comando)).isEqualTo(primeiro) }
        assertThat(consultas.get()).isZero()
        val linha = jdbc.queryForMap("""SELECT l.id, l.recebivel_id, l.moeda_pagamento, l.valor_presente_brl,
            l.desagio_brl, l.valor_pagamento, i.resposta_http, i.situacao, r.status
            FROM liquidacoes l JOIN idempotencia i ON i.liquidacao_id = l.id AND i.organizacao_id = l.organizacao_id
            JOIN recebiveis r ON r.id = l.recebivel_id WHERE l.id = ?""", primeiro.liquidacao.liquidacaoId)
        assertThat(linha["id"]).isEqualTo(primeiro.liquidacao.liquidacaoId)
        assertThat(linha["recebivel_id"]).isEqualTo(primeiro.liquidacao.recebivelId)
        assertThat(linha["moeda_pagamento"]).isEqualTo(primeiro.liquidacao.moeda.name)
        assertThat(linha["valor_presente_brl"] as BigDecimal).isEqualByComparingTo(primeiro.liquidacao.valorPresenteBrl)
        assertThat(linha["desagio_brl"] as BigDecimal).isEqualByComparingTo(primeiro.liquidacao.desagioBrl)
        assertThat(linha["valor_pagamento"] as BigDecimal).isEqualByComparingTo(primeiro.liquidacao.valorPagamento)
        assertThat((linha["resposta_http"] as Number).toInt()).isEqualTo(primeiro.metadado)
        assertThat(linha["situacao"]).isEqualTo("CONCLUIDA")
        assertThat(linha["status"]).isEqualTo("LIQUIDADO")
        listOf("liquidacoes", "idempotencia", "auditoria_liquidacoes").forEach { assertThat(f.contar(it)).isEqualTo(1) }
        assertThrows<ConflitoLiquidacao> { f.servico().liquidar(comando.copy(recebivelId = f.recebivel())) }
        assertThrows<ConflitoLiquidacao> { f.servico().liquidar(comando.copy(moeda = MoedaPagamento.BRL)) }
    }

    @Test
    fun `reserva historica concorrente preserva metadado no conflito e no replay`() {
        val f = Fixture()
        val original = f.servico().liquidar(ComandoLiquidacao(f.recebivel(), MoedaPagamento.BRL, "original"))
        val id = f.recebivel()
        val liquidacaoId = UUID.randomUUID()
        val comando = ComandoLiquidacao(id, MoedaPagamento.BRL, "historico")
        val hash = MessageDigest.getInstance("SHA-256").digest(
            "LIQUIDAR_RECEBIVEL\n$id\nBRL".toByteArray(StandardCharsets.UTF_8),
        )
        dataSource.connection.use { conexao ->
            conexao.autoCommit = false
            try {
                conexao.prepareStatement("""INSERT INTO liquidacoes
                    (id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses, versao_recebivel,
                     taxa_base_id, taxa_base_mensal, taxa_base_vigente_em, spread_id, spread_mensal, spread_vigente_em,
                     valor_presente_brl, desagio_brl, moeda_pagamento, cotacao_cambial_id, taxa_brl_por_unidade,
                     cotacao_vigente_em, cotacao_valida_ate, valor_pagamento, liquidada_em)
                    SELECT ?, organizacao_id, ?, modalidade, valor_face_brl, prazo_meses, versao_recebivel,
                     taxa_base_id, taxa_base_mensal, taxa_base_vigente_em, spread_id, spread_mensal, spread_vigente_em,
                     valor_presente_brl, desagio_brl, moeda_pagamento, cotacao_cambial_id, taxa_brl_por_unidade,
                     cotacao_vigente_em, cotacao_valida_ate, valor_pagamento, liquidada_em
                    FROM liquidacoes WHERE id = ?""").use {
                    it.setObject(1, liquidacaoId)
                    it.setObject(2, id)
                    it.setObject(3, original.liquidacao.liquidacaoId)
                    check(it.executeUpdate() == 1)
                }
                conexao.prepareStatement("UPDATE recebiveis SET status = 'LIQUIDADO', versao = 1 WHERE id = ?").use {
                    it.setObject(1, id)
                    check(it.executeUpdate() == 1)
                }
                conexao.prepareStatement("""INSERT INTO idempotencia
                    (id, organizacao_id, tipo_operacao, chave, hash_requisicao, situacao, liquidacao_id,
                     resposta_http, resposta_corpo, concluido_em)
                    SELECT ?, organizacao_id, tipo_operacao, ?, ?, 'CONCLUIDA', ?, 202,
                        jsonb_set(jsonb_set(resposta_corpo, '{liquidacaoId}', to_jsonb(CAST(? AS text))),
                                  '{recebivelId}', to_jsonb(CAST(? AS text))), CURRENT_TIMESTAMP
                    FROM idempotencia WHERE organizacao_id = ? AND chave = 'original'""").use {
                    it.setObject(1, UUID.randomUUID())
                    it.setString(2, comando.chaveIdempotencia)
                    it.setBytes(3, hash)
                    it.setObject(4, liquidacaoId)
                    it.setString(5, liquidacaoId.toString())
                    it.setString(6, id.toString())
                    it.setObject(7, f.org)
                    check(it.executeUpdate() == 1)
                }
                val pid = conexao.createStatement().use { s -> s.executeQuery("SELECT pg_backend_pid()").use { r -> r.next(); r.getInt(1) } }
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val futuro = executor.submit<ResultadoOperacaoLiquidacao> { f.servico().liquidar(comando) }
                    aguardarBloqueio(pid) { futuro.isDone }
                    conexao.commit()
                    val resultado = futuro.get(20, TimeUnit.SECONDS)
                    assertThat(resultado.metadado).isEqualTo(202)
                    assertThat(resultado.liquidacao.liquidacaoId).isEqualTo(liquidacaoId)
                    assertThat(resultado.liquidacao.recebivelId).isEqualTo(id)
                    assertThat(f.servico().liquidar(comando)).isEqualTo(resultado)
                    assertThat(f.contar("liquidacoes")).isEqualTo(2)
                    assertThat(f.contar("idempotencia")).isEqualTo(2)
                    assertThat(f.contar("auditoria_liquidacoes")).isEqualTo(1)
                } finally {
                    conexao.rollback()
                    executor.shutdownNow()
                    check(executor.awaitTermination(10, TimeUnit.SECONDS))
                }
            } finally {
                conexao.rollback()
            }
        }
    }

    @Test
    fun `falhas injetadas no banco em tres estagios revertem todos os efeitos`() {
        val f = Fixture()
        listOf("liquidacoes" to "INSERT", "auditoria_liquidacoes" to "INSERT", "idempotencia" to "UPDATE")
            .forEach { (tabela, operacao) ->
                val id = f.recebivel()
                val sufixo = UUID.randomUUID().toString().replace("-", "")
                val funcao = "b07_f_$sufixo"
                val trigger = "b07_t_$sufixo"
                jdbc.execute("""CREATE FUNCTION $funcao() RETURNS trigger LANGUAGE plpgsql AS ${'$'}${'$'}
                    BEGIN IF NEW.organizacao_id = '${f.org}'::uuid THEN
                      RAISE EXCEPTION 'falha controlada B07' USING ERRCODE = 'P0001';
                    END IF; RETURN NEW; END ${'$'}${'$'}""")
                try {
                    jdbc.execute("CREATE TRIGGER $trigger BEFORE $operacao ON $tabela FOR EACH ROW EXECUTE FUNCTION $funcao()")
                    val erro = assertThrows<DataAccessException> {
                        f.servico().liquidar(ComandoLiquidacao(id, MoedaPagamento.BRL, "falha-$sufixo"))
                    }
                    assertThat(erro.rootCause?.message).contains("falha controlada B07")
                    f.semEfeitos(id)
                } finally {
                    jdbc.execute("DROP TRIGGER IF EXISTS $trigger ON $tabela")
                    jdbc.execute("DROP FUNCTION $funcao()")
                }
            }
    }

    @Test
    fun `golden cases e snapshots permanecem intactos apos novas versoes e mutacoes proibidas`() {
        val f = Fixture()
        val c1 = f.servico().liquidar(ComandoLiquidacao(f.recebivel(), MoedaPagamento.BRL, "c1"))
        val c2 = f.servico().liquidar(ComandoLiquidacao(
            f.recebivel(Modalidades.CHEQUE_PRE_DATADO, "25000.00", 2), MoedaPagamento.BRL, "c2",
        ))
        val comando = ComandoLiquidacao(f.recebivel(), MoedaPagamento.USD, "c3")
        val c3 = f.servico().liquidar(comando)
        assertThat(c1.liquidacao.valorPresenteBrl).isEqualByComparingTo("92859.94")
        assertThat(c1.liquidacao.desagioBrl).isEqualByComparingTo("7140.06")
        assertThat(c2.liquidacao.valorPresenteBrl).isEqualByComparingTo("23337.77")
        assertThat(c3.liquidacao.valorPagamento).isEqualByComparingTo("17094.67")
        val snapshots = jdbc.queryForList("SELECT * FROM liquidacoes WHERE organizacao_id = ? ORDER BY id", f.org)
        val futuro = f.instante.plusSeconds(1)
        f.taxas.registrarTaxaBase(BigDecimal("0.02"), futuro)
        f.taxas.registrarSpread(Modalidades.DUPLICATA_MERCANTIL, BigDecimal("0.07"), futuro)
        f.taxas.registrarCotacao(BigDecimal("6.00"), futuro)
        assertThat(f.servico().liquidar(comando)).isEqualTo(c3)
        val mutacoes = listOf(
            "UPDATE liquidacoes SET valor_pagamento = 1 WHERE id = '${c3.liquidacao.liquidacaoId}'",
            "DELETE FROM liquidacoes WHERE id = '${c3.liquidacao.liquidacaoId}'",
            "UPDATE auditoria_liquidacoes SET evento = 'ALTERADO' WHERE liquidacao_id = '${c3.liquidacao.liquidacaoId}'",
            "DELETE FROM auditoria_liquidacoes WHERE liquidacao_id = '${c3.liquidacao.liquidacaoId}'",
            "UPDATE idempotencia SET resposta_http = 200 WHERE liquidacao_id = '${c3.liquidacao.liquidacaoId}'",
            "UPDATE taxas_base SET taxa_mensal = 0.1 WHERE id = (SELECT taxa_base_id FROM liquidacoes WHERE id = '${c3.liquidacao.liquidacaoId}')",
            "UPDATE spreads_recebiveis SET spread_mensal = 0.1 WHERE id = (SELECT spread_id FROM liquidacoes WHERE id = '${c3.liquidacao.liquidacaoId}')",
            "DELETE FROM cotacoes_cambiais WHERE id = (SELECT cotacao_cambial_id FROM liquidacoes WHERE id = '${c3.liquidacao.liquidacaoId}')",
        )
        mutacoes.forEach { sql ->
            val erro = assertThrows<DataAccessException> { jdbc.update(sql) }
            assertThat((erro.rootCause as? java.sql.SQLException)?.sqlState).isEqualTo("55000")
        }
        assertThat(jdbc.queryForList("SELECT * FROM liquidacoes WHERE organizacao_id = ? ORDER BY id", f.org))
            .isEqualTo(snapshots)
        listOf("liquidacoes", "idempotencia", "auditoria_liquidacoes").forEach { assertThat(f.contar(it)).isEqualTo(3) }
    }

    @Test
    fun `cotacao e aceita no limite e rejeitada um microssegundo depois`() {
        val f = Fixture()
        val limite = f.instante.plusSeconds(1800)
        val noLimite = Clock.fixed(limite, ZoneOffset.UTC)
        val taxasNoLimite = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), noLimite)
        val repositorioNoLimite = LiquidacaoJdbc(jdbc, TransactionTemplate(manager), noLimite)
        val idAceito = f.recebivel()
        val servico = ServicoLiquidacao(IdentidadeConfiavel { IdentidadeLiquidacao(f.org, f.usuario) },
            RecebiveisJdbc(jdbc), repositorioNoLimite, taxasNoLimite, repositorioNoLimite, noLimite)
        val confirmado = servico.liquidar(ComandoLiquidacao(idAceito, MoedaPagamento.USD, "limite"))
        assertThat(confirmado.liquidacao.valorPagamento).isEqualByComparingTo("17094.67")

        val outro = Fixture()
        val idRejeitado = outro.recebivel()
        val expirado = Clock.fixed(outro.instante.plusSeconds(1800).plusNanos(1000), ZoneOffset.UTC)
        val taxasExpiradas = ManterEConsultarTaxas(HistoricoFinanceiroJdbc(jdbc), SeletorTaxas(Duration.ofMinutes(30)), expirado)
        val repositorioExpirado = LiquidacaoJdbc(jdbc, TransactionTemplate(manager), expirado)
        val servicoExpirado = ServicoLiquidacao(IdentidadeConfiavel { IdentidadeLiquidacao(outro.org, outro.usuario) },
            RecebiveisJdbc(jdbc), repositorioExpirado, taxasExpiradas, repositorioExpirado, expirado)
        assertThrows<DadosFinanceirosIndisponiveis> {
            servicoExpirado.liquidar(ComandoLiquidacao(idRejeitado, MoedaPagamento.USD, "expirada"))
        }
        outro.semEfeitos(idRejeitado)
    }

    @Test
    fun `expiracao apos espera real no update reverte reserva e versao`() {
        val f = Fixture()
        val id = f.recebivel()
        val agora = AtomicReference(f.instante)
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = agora.get()
        }
        val repositorio = LiquidacaoJdbc(jdbc, TransactionTemplate(manager), clock)
        dataSource.connection.use { bloqueador ->
            bloqueador.autoCommit = false
            try {
                bloqueador.prepareStatement("UPDATE recebiveis SET status = status WHERE id = ?").use {
                    it.setObject(1, id)
                    it.executeUpdate()
                }
                val pid = bloqueador.createStatement().use { s -> s.executeQuery("SELECT pg_backend_pid()").use { r -> r.next(); r.getInt(1) } }
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val futuro = executor.submit<ResultadoOperacaoLiquidacao> {
                        f.servico(transacao = repositorio).liquidar(ComandoLiquidacao(id, MoedaPagamento.USD, "espera"))
                    }
                    aguardarBloqueio(pid) { futuro.isDone }
                    agora.set(f.instante.plusSeconds(1801))
                    bloqueador.rollback()
                    val erro = assertThrows<java.util.concurrent.ExecutionException> { futuro.get(20, TimeUnit.SECONDS) }
                    assertThat(erro.cause).isInstanceOf(DadosFinanceirosIndisponiveis::class.java)
                } finally {
                    bloqueador.rollback()
                    executor.shutdownNow()
                    check(executor.awaitTermination(10, TimeUnit.SECONDS))
                }
            } finally {
                bloqueador.rollback()
            }
        }
        f.semEfeitos(id)
    }

    @Test
    fun `reserva concorrente abortada libera espera e permite uma unica confirmacao`() {
        val f = Fixture()
        val id = f.recebivel()
        val comando = ComandoLiquidacao(id, MoedaPagamento.BRL, "reserva")
        val hash = MessageDigest.getInstance("SHA-256").digest(
            "LIQUIDAR_RECEBIVEL\n$id\nBRL".toByteArray(StandardCharsets.UTF_8),
        )
        dataSource.connection.use { bloqueador ->
            bloqueador.autoCommit = false
            try {
                bloqueador.prepareStatement("""INSERT INTO idempotencia
                    (id, organizacao_id, tipo_operacao, chave, hash_requisicao)
                    VALUES (?, ?, 'LIQUIDAR_RECEBIVEL', ?, ?)""").use {
                    it.setObject(1, UUID.randomUUID())
                    it.setObject(2, f.org)
                    it.setString(3, comando.chaveIdempotencia)
                    it.setBytes(4, hash)
                    it.executeUpdate()
                }
                val pid = bloqueador.createStatement().use { s -> s.executeQuery("SELECT pg_backend_pid()").use { r -> r.next(); r.getInt(1) } }
                val executor = Executors.newSingleThreadExecutor()
                try {
                    val futuro = executor.submit<ResultadoOperacaoLiquidacao> { f.servico().liquidar(comando) }
                    aguardarBloqueio(pid) { futuro.isDone }
                    bloqueador.rollback()
                    val resultado = futuro.get(20, TimeUnit.SECONDS)
                    assertThat(f.servico().liquidar(comando)).isEqualTo(resultado)
                    assertThat(jdbc.queryForObject("SELECT situacao FROM idempotencia WHERE organizacao_id = ?",
                        String::class.java, f.org)).isEqualTo("CONCLUIDA")
                    listOf("liquidacoes", "idempotencia", "auditoria_liquidacoes").forEach { assertThat(f.contar(it)).isEqualTo(1) }
                } finally {
                    bloqueador.rollback()
                    executor.shutdownNow()
                    check(executor.awaitTermination(10, TimeUnit.SECONDS))
                }
            } finally {
                bloqueador.rollback()
            }
        }
    }

    private fun aguardarBloqueio(pid: Int, terminou: () -> Boolean) {
        val limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        var bloqueado = false
        while (!bloqueado && System.nanoTime() < limite && !terminou()) {
            bloqueado = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))",
                Boolean::class.java, pid,
            ) == true
            if (!bloqueado) Thread.yield()
        }
        assertThat(bloqueado).describedAs("transação aguardando o bloqueador PostgreSQL").isTrue()
    }

    companion object {
        private val sequencia = AtomicInteger()
    }
}
