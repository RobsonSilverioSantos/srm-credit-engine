package br.com.srm.creditengine.taxas.adaptador.saida

import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.taxas.aplicacao.BoletimPtax
import br.com.srm.creditengine.taxas.aplicacao.PaginaCotacoes
import br.com.srm.creditengine.taxas.aplicacao.PaginaSelecoesCambiais
import br.com.srm.creditengine.taxas.aplicacao.SelecaoFonteCambial
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoCambio
import br.com.srm.creditengine.taxas.dominio.CotacaoAplicavel
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.dominio.FonteCambial
import br.com.srm.creditengine.taxas.dominio.PoliticaElegibilidadeCambial
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import java.math.BigDecimal
import java.security.MessageDigest
import java.sql.ResultSet
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

class HistoricoCambioJdbc(
    private val jdbc: JdbcTemplate,
    private val transacao: TransactionTemplate,
    private val relogio: Clock = Clock.systemUTC(),
    private val modoDemo: Boolean = false,
    private val validadeDemo: Duration = Duration.ofMinutes(30),
) : HistoricoCambio {
    private fun agora() = relogio.instant().truncatedTo(ChronoUnit.MICROS)
    private fun Instant.utc(): OffsetDateTime = atOffset(ZoneOffset.UTC)
    private fun ResultSet.instante(nome: String): Instant = getObject(nome, OffsetDateTime::class.java).toInstant()

    private fun ResultSet.cotacao() = CotacaoCambial(
        getObject("id", UUID::class.java), getBigDecimal("taxa_brl_por_unidade"), instante("vigente_em"), instante("criado_em"),
        FonteCambial.valueOf(getString("fonte")), getInt("versao"), getDate("data_boletim")?.toLocalDate(),
        getString("horario_original"), getObject("autor_id", UUID::class.java), getString("justificativa"),
    )

    private fun ResultSet.selecao() = SelecaoFonteCambial(
        getObject("id", UUID::class.java), FonteCambial.valueOf(getString("fonte")),
        getObject("cotacao_manual_id", UUID::class.java), instante("autorizado_em"),
        getObject("valida_ate", OffsetDateTime::class.java)?.toInstant(), getObject("politica_id", UUID::class.java),
        getObject("autor_id", UUID::class.java), getString("justificativa"),
    )

    override fun registrarManual(autor: IdentidadeUsuario, taxa: BigDecimal, justificativa: String): CotacaoCambial =
        checkNotNull(transacao.execute {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 26))",
            Any::class.java, autor.organizacaoId.toString())
        val instante = agora()
        val id = UUID.randomUUID()
        val versao = jdbc.queryForObject(
            "SELECT coalesce(max(versao), 0) + 1 FROM cotacoes_cambiais WHERE fonte = 'MANUAL' AND organizacao_id = ?",
            Int::class.java, autor.organizacaoId,
        ) ?: 1
        jdbc.update(
            """INSERT INTO cotacoes_cambiais
               (id, taxa_brl_por_unidade, vigente_em, criado_em, fonte, organizacao_id, autor_id, justificativa, versao)
               VALUES (?, ?, ?, ?, 'MANUAL', ?, ?, ?, ?)""",
            id, taxa, instante.utc(), instante.utc(), autor.organizacaoId, autor.usuarioId, justificativa, versao,
        )
        CotacaoCambial(id, taxa, instante, instante, FonteCambial.MANUAL, versao, autorId = autor.usuarioId,
            justificativa = justificativa)
    })

    override fun selecionar(
        autor: IdentidadeUsuario, fonte: FonteCambial, cotacaoId: UUID?, justificativa: String,
    ): SelecaoFonteCambial = checkNotNull(transacao.execute {
        // A seleção só disputa com liquidações desta organização, nunca com simulações.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), 26))",
            Any::class.java, autor.organizacaoId.toString())
        val instante = agora()
        val politica = politica(instante)
        val fim = if (fonte == FonteCambial.MANUAL) {
            val manual = jdbc.query(
                "SELECT * FROM cotacoes_cambiais WHERE id = ? AND organizacao_id = ? AND fonte = 'MANUAL'",
                { rs, _ -> rs.cotacao() }, cotacaoId, autor.organizacaoId,
            ).singleOrNull() ?: throw ConflitoLiquidacao("Cotação manual indisponível nesta organização")
            if (!politica.cadastroManualDoDia(manual.criadoEm, instante)) {
                throw ConflitoLiquidacao("Nova cotação manual é obrigatória no dia da autorização")
            }
            val usada = jdbc.queryForObject(
                "SELECT count(*) FROM selecoes_cambiais WHERE cotacao_manual_id = ?", Long::class.java, cotacaoId,
            ) ?: 0L
            if (usada > 0) throw ConflitoLiquidacao("Cotação manual já autorizada; cadastre nova versão")
            politica.limiteManual(instante)
        } else null
        val id = UUID.randomUUID()
        jdbc.update(
            """INSERT INTO selecoes_cambiais
               (id, organizacao_id, fonte, cotacao_manual_id, autor_id, justificativa, autorizado_em, valida_ate, politica_id)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            id, autor.organizacaoId, fonte.name, cotacaoId, autor.usuarioId, justificativa, instante.utc(), fim?.utc(), politica.id,
        )
        SelecaoFonteCambial(id, fonte, cotacaoId, instante, fim, politica.id, autor.usuarioId, justificativa)
    })

    private fun politica(referencia: Instant) = jdbc.query(
            """SELECT id, versao, ptax_max_idade_segundos FROM politicas_cambiais
               WHERE vigente_em <= ? AND criado_em <= ? ORDER BY vigente_em DESC, criado_em DESC, id DESC LIMIT 1""",
            { rs, _ -> PoliticaElegibilidadeCambial(rs.getObject("id", UUID::class.java),
                rs.getString("versao"), rs.getLong("ptax_max_idade_segundos")) },
            referencia.utc(), referencia.utc(),
        ).singleOrNull() ?: throw DadosFinanceirosIndisponiveis("Política cambial indisponível")

    override fun consultar(organizacaoId: UUID, referencia: Instant): CotacaoAplicavel {
        val politica = politica(referencia)
        val selecao = jdbc.query(
            """SELECT * FROM selecoes_cambiais WHERE organizacao_id = ? AND autorizado_em <= ?
               ORDER BY autorizado_em DESC, revisao DESC LIMIT 1""",
            { rs, _ -> rs.selecao() }, organizacaoId, referencia.utc(),
        ).singleOrNull()
        val fonte = selecao?.fonte ?: if (modoDemo) FonteCambial.DEMO else FonteCambial.BCB_PTAX
        val cotacao = (if (fonte == FonteCambial.MANUAL) {
            jdbc.query(
                """SELECT * FROM cotacoes_cambiais WHERE id = ? AND organizacao_id = ? AND fonte = 'MANUAL'
                   AND vigente_em <= ? AND criado_em <= ?""",
                { rs, _ -> rs.cotacao() }, selecao?.cotacaoId, organizacaoId, referencia.utc(), referencia.utc(),
            ).singleOrNull()
        } else if (fonte == FonteCambial.DEMO) {
            jdbc.query(
                """SELECT * FROM cotacoes_cambiais WHERE fonte = 'DEMO' AND vigente_em <= ? AND criado_em <= ?
                   ORDER BY vigente_em DESC LIMIT 1""",
                { rs, _ -> rs.cotacao() }, referencia.utc(), referencia.utc(),
            ).singleOrNull()
        } else {
            jdbc.query(
                """SELECT * FROM cotacoes_cambiais WHERE fonte = 'BCB_PTAX'
                   AND vigente_em <= ? AND criado_em <= ?
                   ORDER BY data_boletim DESC, versao DESC LIMIT 1""",
                { rs, _ -> rs.cotacao() }, referencia.utc(), referencia.utc(),
            ).singleOrNull()
        }) ?: throw DadosFinanceirosIndisponiveis("Sem cotação da fonte explicitamente selecionada")
        val limite = if (fonte == FonteCambial.MANUAL) checkNotNull(selecao?.validaAte)
        else if (fonte == FonteCambial.DEMO) cotacao.vigenteEm.plus(validadeDemo)
        else politica.limitePtax(cotacao.vigenteEm)
        return CotacaoAplicavel(cotacao, politica.id, selecao?.id,
            selecao?.id?.takeIf { fonte == FonteCambial.MANUAL },
            if (fonte == FonteCambial.MANUAL) checkNotNull(selecao).autorizadoEm else cotacao.criadoEm,
            limite, fonte == FonteCambial.MANUAL).also { it.validar(referencia) }
    }

    override fun historico(organizacaoId: UUID, fonte: FonteCambial?, pagina: Int, tamanho: Int): PaginaCotacoes {
        val filtro = " WHERE (organizacao_id IS NULL OR organizacao_id = ?) AND (CAST(? AS text) IS NULL OR fonte = ?)"
        val total = jdbc.queryForObject("SELECT count(*) FROM cotacoes_cambiais$filtro",
            Long::class.java, organizacaoId, fonte?.name, fonte?.name) ?: 0L
        val itens = jdbc.query(
            "SELECT * FROM cotacoes_cambiais$filtro ORDER BY criado_em DESC, id DESC LIMIT ? OFFSET ?",
            { rs, _ -> rs.cotacao() }, organizacaoId, fonte?.name, fonte?.name, tamanho, pagina.toLong() * tamanho,
        )
        return PaginaCotacoes(itens, pagina, tamanho, total)
    }

    override fun selecoes(organizacaoId: UUID, pagina: Int, tamanho: Int): PaginaSelecoesCambiais {
        val total = jdbc.queryForObject("SELECT count(*) FROM selecoes_cambiais WHERE organizacao_id = ?",
            Long::class.java, organizacaoId) ?: 0L
        val itens = jdbc.query(
            """SELECT * FROM selecoes_cambiais WHERE organizacao_id = ?
               ORDER BY autorizado_em DESC, revisao DESC LIMIT ? OFFSET ?""",
            { rs, _ -> rs.selecao() }, organizacaoId, tamanho, pagina.toLong() * tamanho,
        )
        return PaginaSelecoesCambiais(itens, pagina, tamanho, total)
    }

    override fun sincronizar(boletins: List<BoletimPtax>): Int {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) { "Sincronização PTAX fora de transação financeira" }
        return checkNotNull(transacao.execute {
            jdbc.execute("SELECT pg_advisory_xact_lock(20261122, 26)")
            val observadoEm = agora()
            var inseridas = 0
            for (b in boletins.sortedBy { it.referencia }) {
                require(b.referencia <= observadoEm) { "Boletim futuro" }
                val canonico = "${b.data}|FECHAMENTO|VENDA|BRL|USD|${b.horarioOriginal}|" +
                    "${b.compra.stripTrailingZeros().toPlainString()}|${b.venda.stripTrailingZeros().toPlainString()}"
                val identidade = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonico.toByteArray(Charsets.UTF_8)))
                val existe = jdbc.queryForObject(
                    "SELECT count(*) FROM cotacoes_cambiais WHERE identidade_publicacao = ?", Long::class.java, identidade,
                ) ?: 0L
                if (existe > 0) continue
                val versao = jdbc.queryForObject(
                    "SELECT coalesce(max(versao), 0) + 1 FROM cotacoes_cambiais WHERE fonte = 'BCB_PTAX' AND data_boletim = ?",
                    Int::class.java, b.data,
                ) ?: 1
                inseridas += jdbc.update(
                    """INSERT INTO cotacoes_cambiais
                       (id, taxa_brl_por_unidade, vigente_em, criado_em, fonte, versao, data_boletim,
                        horario_original, fuso, tipo_boletim, identidade_publicacao, cotacao_compra)
                       VALUES (?, ?, ?, ?, 'BCB_PTAX', ?, ?, ?, 'America/Sao_Paulo', 'FECHAMENTO', ?, ?)""",
                    UUID.randomUUID(), b.venda, b.referencia.utc(), observadoEm.utc(), versao,
                    b.data, b.horarioOriginal, identidade, b.compra,
                )
            }
            inseridas
        })
    }

}
