package br.com.srm.creditengine.liquidacao.adaptador.saida

import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdempotenciaLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.AuditoriaLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.PersistenciaLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.TransacaoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ResultadoOperacaoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.Liquidacao
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.liquidacao.dominio.ResultadoLiquidacao
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.taxas.adaptador.saida.ValidadorCambioJdbc
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoCambio
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate

class LiquidacaoJdbc(
    private val jdbc: JdbcTemplate,
    private val transacao: TransactionTemplate,
    private val relogio: Clock,
    private val aoConfirmarAposCommit: (IdentidadeLiquidacao, Liquidacao) -> Unit = { _, _ -> },
    private val aoReproduzir: () -> Unit = {},
    historicoCambio: HistoricoCambio? = null,
) : IdempotenciaLiquidacao, TransacaoLiquidacao, PersistenciaLiquidacao, AuditoriaLiquidacao {
    private val cambio = ValidadorCambioJdbc(jdbc, transacao, relogio, historicoCambio)
    private fun Instant.utc(): OffsetDateTime = atOffset(ZoneOffset.UTC)

    override fun consultar(organizacaoId: UUID, chave: String, hash: ByteArray): ResultadoOperacaoLiquidacao? {
        val resultado = jdbc.query(
            """SELECT i.hash_requisicao, i.situacao, i.resposta_http,
                      i.resposta_corpo->>'liquidacaoId' AS liquidacao_id,
                      i.resposta_corpo->>'recebivelId' AS recebivel_id,
                      i.resposta_corpo->>'moeda' AS moeda,
                      i.resposta_corpo->>'valorPresenteBrl' AS vp,
                      i.resposta_corpo->>'desagioBrl' AS desagio,
                      i.resposta_corpo->>'valorPagamento' AS pagamento,
                      i.resposta_corpo->>'liquidadaEm' AS liquidada_em
               FROM idempotencia i
               WHERE i.organizacao_id = ? AND i.tipo_operacao = 'LIQUIDAR_RECEBIVEL' AND i.chave = ?""",
            { rs, _ ->
                if (!MessageDigest.isEqual(rs.getBytes("hash_requisicao"), hash)) {
                    throw ConflitoLiquidacao("Chave de idempotência já usada com outro comando")
                }
                check(rs.getString("situacao") == "CONCLUIDA") { "Reserva de idempotência não concluída" }
                ResultadoOperacaoLiquidacao(
                    ResultadoLiquidacao(
                        UUID.fromString(rs.getString("liquidacao_id")), UUID.fromString(rs.getString("recebivel_id")),
                        MoedaPagamento.valueOf(rs.getString("moeda")), rs.getBigDecimal("vp"),
                        rs.getBigDecimal("desagio"), rs.getBigDecimal("pagamento"),
                        Instant.parse(rs.getString("liquidada_em")),
                    ),
                    rs.getInt("resposta_http"),
                )
            }, organizacaoId, chave,
        ).singleOrNull()
        if (resultado != null) aoReproduzir()
        return resultado
    }

    override fun confirmar(
        identidade: IdentidadeLiquidacao,
        chave: String,
        hash: ByteArray,
        liquidacao: Liquidacao,
    ): ResultadoOperacaoLiquidacao {
        var novaConfirmacao = false
        val resultado = checkNotNull(transacao.execute {
            val organizacao = identidade.organizacaoId
            val reserva = UUID.randomUUID()
            val inserida = jdbc.update(
                """INSERT INTO idempotencia (id, organizacao_id, tipo_operacao, chave, hash_requisicao)
                   VALUES (?, ?, 'LIQUIDAR_RECEBIVEL', ?, ?) ON CONFLICT (organizacao_id, tipo_operacao, chave) DO NOTHING""",
                reserva, organizacao, chave, hash,
            )
            if (inserida == 0) {
                return@execute checkNotNull(consultar(organizacao, chave, hash)) {
                    "Reserva de idempotência desapareceu"
                }
            }
            val recebivel = liquidacao.recebivel
            check(recebivel.organizacaoId == organizacao) { "Organização inconsistente" }
            cambio.validar(organizacao, liquidacao.taxas)
            val limite = liquidacao.taxas.cotacaoValidaAte
            if (limite != null && relogio.instant() > limite) {
                throw DadosFinanceirosIndisponiveis("Cotação BRL/USD venceu antes da liquidação")
            }
            if (!atualizarRecebivel(liquidacao)) throw ConflitoLiquidacao("Recebível alterado ou já liquidado")
            if (limite != null && relogio.instant() > limite) {
                throw DadosFinanceirosIndisponiveis("Cotação BRL/USD venceu durante a liquidação")
            }
            val taxas = liquidacao.taxas
            val precificacao = liquidacao.precificacao
            inserir(liquidacao)
            val resultado = ResultadoLiquidacao(
                liquidacao.id, recebivel.id, liquidacao.moeda, precificacao.valorPresenteBrl,
                precificacao.desagioBrl, liquidacao.valorPagamento, taxas.referencia,
            )
            registrar(identidade, liquidacao)
            cambio.validar(organizacao, liquidacao.taxas)
            jdbc.update(
                """UPDATE idempotencia SET situacao = 'CONCLUIDA', liquidacao_id = ?, resposta_http = ?,
                   resposta_corpo = jsonb_build_object(
                       'liquidacaoId', CAST(? AS text), 'recebivelId', CAST(? AS text), 'moeda', CAST(? AS text),
                       'valorPresenteBrl', CAST(? AS text), 'desagioBrl', CAST(? AS text),
                       'valorPagamento', CAST(? AS text), 'liquidadaEm', CAST(? AS text)),
                   concluido_em = CURRENT_TIMESTAMP WHERE id = ?""",
                resultado.liquidacaoId, 201, resultado.liquidacaoId.toString(),
                resultado.recebivelId.toString(), resultado.moeda.name, resultado.valorPresenteBrl.toPlainString(),
                resultado.desagioBrl.toPlainString(), resultado.valorPagamento.toPlainString(),
                resultado.liquidadaEm.toString(), reserva,
            )
            novaConfirmacao = true
            ResultadoOperacaoLiquidacao(resultado, 201)
        })
        if (novaConfirmacao) aoConfirmarAposCommit(identidade, liquidacao)
        return resultado
    }

    override fun atualizarRecebivel(liquidacao: Liquidacao): Boolean {
        val recebivel = liquidacao.recebivel
        return jdbc.update(
            """UPDATE recebiveis SET status = 'LIQUIDADO', versao = versao + 1, atualizado_em = CURRENT_TIMESTAMP
               WHERE organizacao_id = ? AND id = ? AND versao = ? AND status = 'DISPONIVEL'""",
            recebivel.organizacaoId, recebivel.id, recebivel.versao,
        ) == 1
    }

    override fun inserir(liquidacao: Liquidacao) {
        val recebivel = liquidacao.recebivel
        val taxas = liquidacao.taxas
        val cotacao = taxas.cotacao
        val precificacao = liquidacao.precificacao
        jdbc.update(
            """INSERT INTO liquidacoes
               (id, organizacao_id, recebivel_id, modalidade, valor_face_brl, prazo_meses, versao_recebivel,
                taxa_base_id, taxa_base_mensal, taxa_base_vigente_em, spread_id, spread_mensal, spread_vigente_em,
                valor_presente_brl, desagio_brl, moeda_pagamento, cotacao_cambial_id, taxa_brl_por_unidade,
                cotacao_vigente_em, cotacao_valida_ate, valor_pagamento, liquidada_em,
                politica_cambial_id, selecao_cambial_id, cambio_snapshot)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                 CASE WHEN CAST(? AS text) IS NULL THEN NULL ELSE jsonb_build_object(
                   'fonte', CAST(? AS text), 'lado', 'VENDA', 'versaoCotacao', CAST(? AS integer),
                   'cotacaoId', CAST(? AS text), 'referencia', CAST(? AS text), 'observadaEm', CAST(? AS text),
                   'dataBoletim', CAST(? AS text), 'horarioOriginal', CAST(? AS text),
                   'fuso', 'America/Sao_Paulo', 'boletim', CAST(? AS text),
                   'politicaId', CAST(? AS text), 'selecaoId', CAST(? AS text), 'autorizacaoId', CAST(? AS text),
                   'validaDesde', CAST(? AS text), 'validaAte', CAST(? AS text), 'limiteExclusivo', CAST(? AS boolean),
                   'corte', CAST(? AS text), 'autorId', CAST(? AS text), 'justificativa', CAST(? AS text))
                 END)""",
            liquidacao.id, recebivel.organizacaoId, recebivel.id, recebivel.modalidade, recebivel.valorFaceBrl,
            recebivel.prazoMeses, recebivel.versao + 1, taxas.taxaBase.id, taxas.taxaBase.taxaMensal,
            taxas.taxaBase.vigenteEm.utc(), taxas.spread.id, taxas.spread.spreadMensal, taxas.spread.vigenteEm.utc(),
            precificacao.valorPresenteBrl, precificacao.desagioBrl, liquidacao.moeda.name, cotacao?.id,
            cotacao?.taxaBrlPorUsd, cotacao?.vigenteEm?.utc(), taxas.cotacaoValidaAte?.utc(), liquidacao.valorPagamento,
            taxas.referencia.utc(),
            taxas.cambio?.politicaId, taxas.cambio?.selecaoId, cotacao?.fonte?.name,
            cotacao?.fonte?.name, cotacao?.versao, cotacao?.id?.toString(), cotacao?.vigenteEm?.toString(),
            cotacao?.criadoEm?.toString(), cotacao?.dataBoletim?.toString(), cotacao?.horarioOriginal,
            if (cotacao?.fonte == br.com.srm.creditengine.taxas.dominio.FonteCambial.BCB_PTAX) "FECHAMENTO" else null,
            taxas.cambio?.politicaId?.toString(), taxas.cambio?.selecaoId?.toString(),
            taxas.cambio?.autorizacaoId?.toString(), taxas.cambio?.validaDesde?.toString(),
            taxas.cotacaoValidaAte?.toString(), taxas.cambio?.limiteExclusivo ?: false,
            taxas.referencia.toString(), cotacao?.autorId?.toString(), cotacao?.justificativa,
        )
    }

    override fun registrar(identidade: IdentidadeLiquidacao, liquidacao: Liquidacao) {
        jdbc.update(
            """INSERT INTO auditoria_liquidacoes (id, organizacao_id, liquidacao_id, usuario_id, evento, detalhes)
               VALUES (?, ?, ?, ?, 'LIQUIDACAO_CONFIRMADA', jsonb_build_object('recebivelId', CAST(? AS text)))""",
            UUID.randomUUID(), identidade.organizacaoId, liquidacao.id, identidade.usuarioId,
            liquidacao.recebivel.id.toString(),
        )
    }
}
