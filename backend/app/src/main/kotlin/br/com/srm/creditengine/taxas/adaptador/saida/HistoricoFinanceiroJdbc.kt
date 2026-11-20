package br.com.srm.creditengine.taxas.adaptador.saida

import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoFinanceiroPorta
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.HistoricoElegivel
import br.com.srm.creditengine.taxas.dominio.SpreadRecebivel
import br.com.srm.creditengine.taxas.dominio.TaxaBase
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate

class HistoricoFinanceiroJdbc(private val jdbc: JdbcTemplate) : HistoricoFinanceiroPorta {
    private fun Instant.utc(): OffsetDateTime = atOffset(ZoneOffset.UTC)
    private fun ResultSet.instante(nome: String): Instant = getObject(nome, OffsetDateTime::class.java).toInstant()

    override fun incluir(taxa: TaxaBase) {
        jdbc.update(
            "INSERT INTO taxas_base (id, taxa_mensal, vigente_em, criado_em) VALUES (?, ?, ?, ?)",
            taxa.id, taxa.taxaMensal, taxa.vigenteEm.utc(), taxa.criadoEm.utc(),
        )
    }

    override fun incluir(spread: SpreadRecebivel) {
        jdbc.update(
            "INSERT INTO spreads_recebiveis (id, modalidade, spread_mensal, vigente_em, criado_em) VALUES (?, ?, ?, ?, ?)",
            spread.id, spread.modalidade, spread.spreadMensal, spread.vigenteEm.utc(), spread.criadoEm.utc(),
        )
    }

    override fun incluir(cotacao: CotacaoCambial) {
        jdbc.update(
            "INSERT INTO cotacoes_cambiais (id, taxa_brl_por_unidade, vigente_em, criado_em) VALUES (?, ?, ?, ?)",
            cotacao.id, cotacao.taxaBrlPorUsd, cotacao.vigenteEm.utc(), cotacao.criadoEm.utc(),
        )
    }

    override fun consultar(modalidade: String, referencia: Instant, incluirCotacao: Boolean): HistoricoElegivel {
        val sql = """
            SELECT b.id AS b_id, b.taxa_mensal, b.vigente_em AS b_vigente, b.criado_em AS b_criado,
                   s.id AS s_id, s.modalidade, s.spread_mensal, s.vigente_em AS s_vigente, s.criado_em AS s_criado,
                   c.id AS c_id, c.taxa_brl_por_unidade, c.vigente_em AS c_vigente, c.criado_em AS c_criado
            FROM (SELECT CAST(? AS timestamptz) AS referencia) r
            LEFT JOIN LATERAL (
                SELECT * FROM taxas_base WHERE vigente_em <= r.referencia AND criado_em <= r.referencia
                ORDER BY vigente_em DESC LIMIT 1
            ) b ON true
            LEFT JOIN LATERAL (
                SELECT * FROM spreads_recebiveis
                WHERE modalidade = ? AND vigente_em <= r.referencia AND criado_em <= r.referencia
                ORDER BY vigente_em DESC LIMIT 1
            ) s ON true
            LEFT JOIN LATERAL (
                SELECT * FROM cotacoes_cambiais
                WHERE ? AND moeda_base = 'BRL' AND moeda_cotada = 'USD'
                  AND vigente_em <= r.referencia AND criado_em <= r.referencia
                ORDER BY vigente_em DESC LIMIT 1
            ) c ON true
        """.trimIndent()
        return jdbc.queryForObject(sql, { rs, _ ->
            val base = rs.getObject("b_id", UUID::class.java)?.let {
                TaxaBase(it, rs.getBigDecimal("taxa_mensal"), rs.instante("b_vigente"), rs.instante("b_criado"))
            }
            val spread = rs.getObject("s_id", UUID::class.java)?.let {
                SpreadRecebivel(it, rs.getString("modalidade"), rs.getBigDecimal("spread_mensal"),
                    rs.instante("s_vigente"), rs.instante("s_criado"))
            }
            val cotacao = rs.getObject("c_id", UUID::class.java)?.let {
                CotacaoCambial(it, rs.getBigDecimal("taxa_brl_por_unidade"), rs.instante("c_vigente"), rs.instante("c_criado"))
            }
            HistoricoElegivel(base, spread, cotacao)
        }, referencia.utc(), modalidade, incluirCotacao) ?: error("Consulta temporal não retornou resultado")
    }
}
