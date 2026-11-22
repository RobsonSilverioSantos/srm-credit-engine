package br.com.srm.creditengine.lotes.adaptador.saida

import br.com.srm.creditengine.cadastro.adaptador.saida.RecebiveisJdbc
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.lotes.aplicacao.ConflitoImportacaoLote
import br.com.srm.creditengine.lotes.aplicacao.ItemCadastradoLote
import br.com.srm.creditengine.lotes.aplicacao.ItemImportacao
import br.com.srm.creditengine.lotes.aplicacao.ResultadoCadastroLote
import br.com.srm.creditengine.lotes.aplicacao.ReferenciaExistenteLote
import br.com.srm.creditengine.lotes.aplicacao.StatusLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.porta.saida.PersistenciaLote
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

class LotesJdbc(private val jdbc: JdbcTemplate, private val transacao: TransactionTemplate) : PersistenciaLote {
    private val recebiveis = RecebiveisJdbc(jdbc)
    private val nomeado = NamedParameterJdbcTemplate(jdbc)

    override fun consultarReferencias(organizacaoId: UUID, referencias: Set<String>): Map<String, ReferenciaExistenteLote> {
        if (referencias.isEmpty()) return emptyMap()
        return nomeado.query(
            """SELECT i.referencia_externa, r.status, r.id
               FROM importacoes_recebiveis_itens i
               JOIN recebiveis r ON r.id = i.recebivel_id AND r.organizacao_id = i.organizacao_id
               WHERE i.organizacao_id = :org AND i.referencia_externa IN (:refs)""",
            mapOf("org" to organizacaoId, "refs" to referencias),
            RowMapper { rs, _ ->
                rs.getString(1) to ReferenciaExistenteLote(
                    rs.getObject(3, UUID::class.java),
                    if (rs.getString(2) == "LIQUIDADO") StatusLinhaLote.JA_LIQUIDADO else StatusLinhaLote.JA_CADASTRADO,
                )
            },
        ).toMap()
    }

    override fun confirmar(
        identidade: IdentidadeUsuario,
        chave: String,
        hash: String,
        itens: List<ItemImportacao>,
        revalidar: () -> Unit,
    ): ResultadoCadastroLote = try {
        checkNotNull(transacao.execute {
            val org = identidade.organizacaoId
            val loteId = UUID.randomUUID()
            val inserido = jdbc.update(
                """INSERT INTO importacoes_recebiveis (id, organizacao_id, usuario_id, chave, hash)
                   VALUES (?, ?, ?, ?, ?) ON CONFLICT (organizacao_id, chave) DO NOTHING""",
                loteId, org, identidade.usuarioId, chave, hash,
            )
            if (inserido == 0) {
                val lote = jdbc.queryForMap(
                    "SELECT id, hash FROM importacoes_recebiveis WHERE organizacao_id = ? AND chave = ?", org, chave,
                )
                if (lote["hash"] != hash) throw ConflitoImportacaoLote()
                resultado(org, lote.getValue("id") as UUID)
            } else {
                // A ordenação das travas impede ciclos entre lotes com as mesmas referências em ordens diferentes.
                val cedentes = itens.map { it.cedenteId }.distinct().sorted()
                for (cedenteId in cedentes) {
                    jdbc.query(
                        "SELECT id FROM cedentes WHERE organizacao_id = ? AND id = ? FOR SHARE",
                        RowMapper { rs, _ -> rs.getObject(1, UUID::class.java) }, org, cedenteId,
                    )
                }
                revalidar()
                for ((posicao, item) in itens.withIndex().sortedBy { it.value.referenciaExterna }) {
                    val recebivelId = UUID.randomUUID()
                    recebiveis.cadastrar(Recebivel(
                        recebivelId, org, item.cedenteId, item.modalidade, item.valorFaceBrl.setScale(2),
                        item.prazoMeses, item.vencimento,
                    ))
                    jdbc.update(
                        """INSERT INTO importacoes_recebiveis_itens
                           (lote_id, organizacao_id, referencia_externa, recebivel_id, posicao, moeda_pagamento_indicativa)
                           VALUES (?, ?, ?, ?, ?, ?)""",
                        loteId, org, item.referenciaExterna, recebivelId, posicao, item.moedaPagamento.name,
                    )
                }
                resultado(org, loteId)
            }
        })
    } catch (_: DataIntegrityViolationException) {
        // Fora da transação: a restrição pode ter invalidado o comando inteiro.
        throw ConflitoImportacaoLote()
    }

    private fun resultado(org: UUID, loteId: UUID) = ResultadoCadastroLote(loteId, jdbc.query(
        """SELECT referencia_externa, recebivel_id FROM importacoes_recebiveis_itens
           WHERE organizacao_id = ? AND lote_id = ? ORDER BY posicao""",
        RowMapper { rs, _ -> ItemCadastradoLote(rs.getString(1), rs.getObject(2, UUID::class.java)) },
        org, loteId,
    ))
}
