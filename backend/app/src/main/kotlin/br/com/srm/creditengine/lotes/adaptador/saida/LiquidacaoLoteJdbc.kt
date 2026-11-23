package br.com.srm.creditengine.lotes.adaptador.saida

import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.cadastro.dominio.StatusRecebivel
import br.com.srm.creditengine.liquidacao.adaptador.saida.LiquidacaoJdbc
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.Liquidacao
import br.com.srm.creditengine.liquidacao.dominio.RecebivelNaoEncontrado
import br.com.srm.creditengine.lotes.aplicacao.ItemLiquidadoLote
import br.com.srm.creditengine.lotes.aplicacao.ResultadoLiquidacaoLote
import br.com.srm.creditengine.lotes.aplicacao.porta.saida.PersistenciaLiquidacaoLote
import br.com.srm.creditengine.taxas.adaptador.saida.ValidadorCambioJdbc
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoCambio
import java.security.MessageDigest
import java.time.Clock
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

class LiquidacaoLoteJdbc(
    private val jdbc: JdbcTemplate,
    private val transacao: TransactionTemplate,
    relogio: Clock,
    private val json: ObjectMapper,
    private val aposCommit: (Int) -> Unit = {},
    private val aposReplay: () -> Unit = {},
    historicoCambio: HistoricoCambio? = null,
) : PersistenciaLiquidacaoLote {
    private val financeiro = LiquidacaoJdbc(jdbc, transacao, relogio)
    private val cambio = ValidadorCambioJdbc(jdbc, transacao, relogio, historicoCambio)

    override fun recebiveis(organizacaoId: UUID, loteId: UUID): List<Recebivel> {
        val itens = jdbc.query(
            """SELECT r.* FROM importacoes_recebiveis_itens i
               JOIN recebiveis r ON r.id = i.recebivel_id AND r.organizacao_id = i.organizacao_id
               WHERE i.organizacao_id = ? AND i.lote_id = ? ORDER BY CAST(r.id AS text)""",
            { rs, _ ->
                Recebivel(rs.getObject("id", UUID::class.java), organizacaoId,
                    rs.getObject("cedente_id", UUID::class.java), rs.getString("modalidade"),
                    rs.getBigDecimal("valor_face_brl"), rs.getInt("prazo_meses"),
                    rs.getDate("vencimento")?.toLocalDate(), StatusRecebivel.valueOf(rs.getString("status")),
                    rs.getLong("versao"))
            }, organizacaoId, loteId,
        )
        if (itens.isEmpty()) throw RecebivelNaoEncontrado()
        return itens
    }

    override fun confirmar(
        identidade: IdentidadeLiquidacao, loteId: UUID, chave: String, hash: ByteArray,
        preparar: () -> List<Liquidacao>, revalidar: (List<Liquidacao>) -> Unit,
    ): ResultadoLiquidacaoLote {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "Liquidação do lote deve iniciar fora de transação existente."
        }
        var quantidade = 0
        val resultado = checkNotNull(transacao.execute {
            val org = identidade.organizacaoId
            val reserva = UUID.randomUUID()
            val inserida = jdbc.update(
                """INSERT INTO idempotencia (id, organizacao_id, tipo_operacao, chave, hash_requisicao)
                   VALUES (?, ?, 'LIQUIDAR_LOTE', ?, ?)
                   ON CONFLICT (organizacao_id, tipo_operacao, chave) DO NOTHING""",
                reserva, org, chave, hash,
            )
            if (inserida == 0) {
                val registro = jdbc.queryForMap(
                    """SELECT hash_requisicao, resposta_corpo::text AS corpo FROM idempotencia
                       WHERE organizacao_id = ? AND tipo_operacao = 'LIQUIDAR_LOTE' AND chave = ?""",
                    org, chave,
                )
                if (!MessageDigest.isEqual(registro["hash_requisicao"] as ByteArray, hash)) {
                    throw ConflitoLiquidacao("Chave de idempotência já usada com outro comando.")
                }
                return@execute lerResultado(registro["corpo"] as String)
            }
            val liquidacoes = preparar().sortedBy { it.recebivel.id.toString() }
            check(liquidacoes.isNotEmpty())
            for (liquidacao in liquidacoes) {
                check(liquidacao.recebivel.organizacaoId == org)
                cambio.validar(org, liquidacao.taxas)
                if (!financeiro.atualizarRecebivel(liquidacao)) {
                    throw ConflitoLiquidacao("Recebível alterado; nenhuma liquidação do lote foi confirmada.")
                }
                financeiro.inserir(liquidacao)
                financeiro.registrar(identidade, liquidacao)
            }
            revalidar(liquidacoes)
            liquidacoes.forEach { cambio.validar(org, it.taxas) }
            val resultado = ResultadoLiquidacaoLote(loteId, liquidacoes.first().taxas.referencia.toString(),
                liquidacoes.map {
                    ItemLiquidadoLote(it.id, it.recebivel.id, it.moeda.name,
                        it.precificacao.valorPresenteBrl.toPlainString(), it.precificacao.desagioBrl.toPlainString(),
                        it.valorPagamento.toPlainString())
                })
            jdbc.update(
                """UPDATE idempotencia SET situacao = 'CONCLUIDA', resposta_http = 201,
                   resposta_corpo = CAST(? AS jsonb), concluido_em = CURRENT_TIMESTAMP WHERE id = ?""",
                json.writeValueAsString(resultado), reserva,
            )
            quantidade = liquidacoes.size
            resultado
        })
        if (quantidade > 0) aposCommit(quantidade) else aposReplay()
        return resultado
    }

    private fun lerResultado(corpo: String): ResultadoLiquidacaoLote {
        val raiz = json.readTree(corpo)
        val itens = raiz.get("itens")
        return ResultadoLiquidacaoLote(UUID.fromString(raiz.get("loteId").stringValue()),
            raiz.get("liquidadaEm").stringValue(), (0 until itens.size()).map {
                val item = itens.get(it)
                ItemLiquidadoLote(UUID.fromString(item.get("liquidacaoId").stringValue()),
                    UUID.fromString(item.get("recebivelId").stringValue()), item.get("moedaPagamento").stringValue(),
                    item.get("valorPresenteBrl").stringValue(), item.get("desagioBrl").stringValue(),
                    item.get("valorPagamento").stringValue())
            })
    }
}
