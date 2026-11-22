package br.com.srm.creditengine.extrato.adaptador.saida

import br.com.srm.creditengine.extrato.aplicacao.FiltroExtrato
import br.com.srm.creditengine.extrato.aplicacao.ItemExtrato
import br.com.srm.creditengine.extrato.aplicacao.LeituraExtrato
import br.com.srm.creditengine.extrato.aplicacao.PaginaExtrato
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

class ExtratoJdbc(jdbc: JdbcTemplate) : LeituraExtrato {
    private val jdbc = NamedParameterJdbcTemplate(jdbc)

    override fun consultar(organizacaoId: UUID, filtro: FiltroExtrato): PaginaExtrato {
        val parametros = MapSqlParameterSource()
            .addValue("organizacao", organizacaoId)
            .addValue("limite", filtro.tamanho)
            .addValue("offset", filtro.pagina.toLong() * filtro.tamanho)
        val condicoes = mutableListOf("l.organizacao_id = :organizacao")
        filtro.inicio?.let {
            condicoes += "l.liquidada_em >= :inicio"
            parametros.addValue("inicio", it.atStartOfDay().atOffset(ZoneOffset.UTC))
        }
        filtro.fim?.let {
            condicoes += "l.liquidada_em < :fim"
            parametros.addValue("fim", it.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC))
        }
        filtro.cedenteId?.let {
            condicoes += "r.cedente_id = :cedente"
            parametros.addValue("cedente", it)
        }
        filtro.moeda?.let {
            condicoes += "l.moeda_pagamento = :moeda"
            parametros.addValue("moeda", it.name)
        }
        val sql = """
            WITH filtradas AS NOT MATERIALIZED (
                SELECT l.*, r.cedente_id, c.nome AS cedente_nome,
                       l.cambio_snapshot->>'fonte' AS cambio_fonte,
                       l.cambio_snapshot->>'lado' AS cambio_lado,
                       (l.cambio_snapshot->>'versaoCotacao')::integer AS cambio_versao,
                       l.cambio_snapshot->>'observadaEm' AS cambio_observada,
                       l.cambio_snapshot->>'autorizacaoId' AS cambio_autorizacao
                FROM liquidacoes l
                JOIN recebiveis r ON r.id = l.recebivel_id AND r.organizacao_id = l.organizacao_id
                JOIN cedentes c ON c.id = r.cedente_id AND c.organizacao_id = l.organizacao_id
                WHERE ${condicoes.joinToString(" AND ")}
            )
            SELECT pagina.*, totais.total
            FROM (SELECT count(*) AS total FROM filtradas) totais
            LEFT JOIN LATERAL (
                SELECT * FROM filtradas ORDER BY liquidada_em DESC, id DESC
                LIMIT :limite OFFSET :offset
            ) pagina ON true
            ORDER BY pagina.liquidada_em DESC, pagina.id DESC
        """.trimIndent()
        var total = 0L
        val itens = mutableListOf<ItemExtrato>()
        jdbc.query(sql, parametros) { rs ->
            total = rs.getLong("total")
            if (rs.getObject("id") != null) itens += rs.item()
        }
        return PaginaExtrato(itens, filtro.pagina, filtro.tamanho, total)
    }

    private fun ResultSet.item() = ItemExtrato(
        getObject("id", UUID::class.java), getObject("recebivel_id", UUID::class.java),
        getObject("cedente_id", UUID::class.java), getString("cedente_nome"), getString("modalidade"), getBigDecimal("valor_face_brl"),
        getInt("prazo_meses"), getLong("versao_recebivel"), getObject("taxa_base_id", UUID::class.java),
        getBigDecimal("taxa_base_mensal"), getObject("taxa_base_vigente_em", OffsetDateTime::class.java).toInstant(),
        getObject("spread_id", UUID::class.java), getBigDecimal("spread_mensal"),
        getObject("spread_vigente_em", OffsetDateTime::class.java).toInstant(),
        getBigDecimal("valor_presente_brl"), getBigDecimal("desagio_brl"),
        MoedaPagamento.valueOf(getString("moeda_pagamento")), getObject("cotacao_cambial_id", UUID::class.java),
        getBigDecimal("taxa_brl_por_unidade"), getObject("cotacao_vigente_em", OffsetDateTime::class.java)?.toInstant(),
        getObject("cotacao_valida_ate", OffsetDateTime::class.java)?.toInstant(), getBigDecimal("valor_pagamento"),
        getObject("liquidada_em", OffsetDateTime::class.java).toInstant(),
        getString("cambio_fonte"), getString("cambio_lado"), getObject("cambio_versao", Int::class.javaObjectType),
        getObject("politica_cambial_id", UUID::class.java), getObject("selecao_cambial_id", UUID::class.java),
        getString("cambio_observada"), getString("cambio_autorizacao"),
    )
}
