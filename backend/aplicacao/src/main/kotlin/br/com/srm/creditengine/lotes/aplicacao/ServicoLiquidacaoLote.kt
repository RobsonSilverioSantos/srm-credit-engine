package br.com.srm.creditengine.lotes.aplicacao

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Cedentes
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.cadastro.dominio.StatusRecebivel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.Liquidacao
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.lotes.aplicacao.porta.entrada.LiquidarLote
import br.com.srm.creditengine.lotes.aplicacao.porta.saida.PersistenciaLiquidacaoLote
import br.com.srm.creditengine.precificacao.aplicacao.precificarComTaxas
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.HexFormat
import java.util.UUID

class ServicoLiquidacaoLote(
    private val identidade: IdentidadeConfiavel,
    private val cedentes: Cedentes,
    private val persistencia: PersistenciaLiquidacaoLote,
    private val taxas: TaxasPorta,
    private val relogio: Clock,
    private val maxTitulos: Int,
) : LiquidarLote {
    init { require(maxTitulos > 0) }

    override fun previa(loteId: UUID, itens: List<ItemLiquidacaoLote>): PreviaLiquidacaoLote {
        validarItens(itens)
        val org = identidade.identidade().organizacaoId
        val recebiveis = persistencia.recebiveis(org, loteId)
        validarCompletude(recebiveis, itens)
        val referencia = agora()
        val moedas = itens.associate { it.recebivelId to it.moedaPagamento }
        val liquidacoes = mutableListOf<Liquidacao>()
        val previas = recebiveis.map { recebivel ->
            val moeda = moedas.getValue(recebivel.id)
            val impedimento = when {
                recebivel.status != StatusRecebivel.DISPONIVEL -> "JA_LIQUIDADO"
                cedentes.buscar(org, recebivel.cedenteId)?.ativo != true -> "CEDENTE_INDISPONIVEL"
                else -> null
            }
            if (impedimento != null) itemInvalido(recebivel, moeda, impedimento) else try {
                precificar(recebivel, moeda, referencia).also { liquidacoes.add(it) }.previa()
            } catch (_: DadosFinanceirosIndisponiveis) {
                itemInvalido(recebivel, moeda, "DADOS_FINANCEIROS_INDISPONIVEIS")
            } catch (_: ArithmeticException) {
                itemInvalido(recebivel, moeda, "PRECIFICACAO_INVALIDA")
            } catch (_: IllegalArgumentException) {
                itemInvalido(recebivel, moeda, "PRECIFICACAO_INVALIDA")
            }
        }
        val apto = liquidacoes.size == recebiveis.size
        return PreviaLiquidacaoLote(
            loteId, referencia.toString(), apto,
            if (apto) revisao(org, loteId, liquidacoes) else null, previas,
            liquidacoes.groupBy { it.moeda.name }.mapValues { (_, valores) ->
                valores.fold(BigDecimal.ZERO) { total, item -> total + item.valorPagamento }.toPlainString()
            },
        )
    }

    override fun confirmar(
        loteId: UUID, itens: List<ItemLiquidacaoLote>, hashPrevia: String,
        confirmacaoFinanceira: Boolean, chave: String,
    ): ResultadoLiquidacaoLote {
        validarItens(itens)
        require(confirmacaoFinanceira) { "Confirmação financeira explícita obrigatória." }
        require(hashPrevia.matches(Regex("[0-9a-f]{64}"))) { "Hash da prévia inválido." }
        require(chave.length in 1..128 && chave.all { it.code in 33..126 }) { "Chave inválida." }
        val usuario = identidade.identidade()
        val moedas = itens.associate { it.recebivelId to it.moedaPagamento }
        val canonico = "LIQUIDAR_LOTE\n$loteId\n$hashPrevia\n" +
            itens.sortedBy { it.recebivelId.toString() }.joinToString("\n") { "${it.recebivelId}|${it.moedaPagamento}" }
        return persistencia.confirmar(usuario, loteId, chave, digest(canonico), preparar = {
            val recebiveis = persistencia.recebiveis(usuario.organizacaoId, loteId)
            validarCompletude(recebiveis, itens)
            val referencia = agora()
            val liquidacoes = recebiveis.map {
                if (it.status != StatusRecebivel.DISPONIVEL ||
                    cedentes.buscar(usuario.organizacaoId, it.cedenteId)?.ativo != true) {
                    throw ConflitoLiquidacao("Lote indisponível; obtenha nova prévia.")
                }
                precificar(it, moedas.getValue(it.id), referencia)
            }
            exigirRevisao(usuario.organizacaoId, loteId, liquidacoes, hashPrevia)
            liquidacoes
        }, revalidar = { liquidacoes ->
            val referencia = agora()
            val atuais = liquidacoes.map {
                if (cedentes.buscar(usuario.organizacaoId, it.recebivel.cedenteId)?.ativo != true) {
                    throw ConflitoLiquidacao("Cedente indisponível; obtenha nova prévia.")
                }
                precificar(it.recebivel, it.moeda, referencia)
            }
            exigirRevisao(usuario.organizacaoId, loteId, atuais, hashPrevia)
        })
    }

    private fun validarItens(itens: List<ItemLiquidacaoLote>) {
        require(itens.size in 1..maxTitulos && itens.map { it.recebivelId }.distinct().size == itens.size) {
            "Quantidade inválida ou recebível repetido."
        }
    }

    private fun validarCompletude(recebiveis: List<Recebivel>, itens: List<ItemLiquidacaoLote>) {
        require(recebiveis.size <= maxTitulos && recebiveis.map { it.id }.toSet() == itens.map { it.recebivelId }.toSet()) {
            "Todos e somente os recebíveis do lote devem ser informados."
        }
    }

    private fun agora() = relogio.instant().truncatedTo(ChronoUnit.MICROS)

    private fun precificar(recebivel: Recebivel, moeda: MoedaPagamento, referencia: Instant): Liquidacao {
        val snapshot = taxas.consultarEm(recebivel.modalidade, moeda == MoedaPagamento.USD, referencia)
        val preco = precificarComTaxas(recebivel.modalidade, recebivel.valorFaceBrl, recebivel.prazoMeses, snapshot)
        require(preco.valorPresenteBrl.signum() > 0 &&
            (moeda != MoedaPagamento.USD || checkNotNull(preco.valorPresenteUsd).signum() > 0)) {
            "Valor financeiro não representável em centavos."
        }
        return Liquidacao(UUID.randomUUID(), recebivel, snapshot, preco, moeda,
            if (moeda == MoedaPagamento.USD) checkNotNull(preco.valorPresenteUsd) else preco.valorPresenteBrl)
    }

    private fun Liquidacao.previa() = PreviaItemLiquidacao(
        recebivel.id, recebivel.versao, recebivel.status.name, moeda.name, recebivel.valorFaceBrl.toPlainString(),
        precificacao.valorPresenteBrl.toPlainString(), precificacao.desagioBrl.toPlainString(), valorPagamento.toPlainString(),
        taxas.taxaBase.id, taxas.taxaBase.taxaMensal.toPlainString(), taxas.taxaBase.vigenteEm.toString(),
        taxas.spread.id, taxas.spread.spreadMensal.toPlainString(), taxas.spread.vigenteEm.toString(),
        taxas.cotacao?.id, taxas.cotacao?.taxaBrlPorUsd?.toPlainString(), taxas.cotacao?.vigenteEm?.toString(),
        taxas.cotacaoValidaAte?.toString(), taxas.cotacao?.fonte?.name,
    )

    private fun itemInvalido(r: Recebivel, moeda: MoedaPagamento, motivo: String) =
        PreviaItemLiquidacao(r.id, r.versao, r.status.name, moeda.name, r.valorFaceBrl.toPlainString(), impedimento = motivo)

    private fun exigirRevisao(org: UUID, loteId: UUID, itens: List<Liquidacao>, hash: String) {
        if (revisao(org, loteId, itens) != hash) throw ConflitoLiquidacao("Prévia obsoleta; obtenha nova prévia.")
    }

    private fun revisao(org: UUID, loteId: UUID, itens: List<Liquidacao>): String {
        // O instante não reserva preço: a revisão identifica dados, versões e snapshots, não a hora da consulta.
        val canonico = "$org\n$loteId\n" + itens.sortedBy { it.recebivel.id.toString() }.joinToString("\n") {
            listOf(it.recebivel.id, it.recebivel.cedenteId, it.recebivel.modalidade, it.recebivel.versao,
                it.recebivel.valorFaceBrl.toPlainString(), it.recebivel.prazoMeses, it.moeda,
                it.taxas.taxaBase.id, it.taxas.spread.id, it.taxas.cotacao?.id, it.taxas.cotacaoValidaAte,
                it.taxas.cambio?.politicaId, it.taxas.cambio?.selecaoId, it.taxas.cambio?.autorizacaoId,
                it.precificacao.valorPresenteBrl.toPlainString(), it.precificacao.desagioBrl.toPlainString(),
                it.valorPagamento.toPlainString()).joinToString("|")
        }
        return HexFormat.of().formatHex(digest(canonico))
    }

    private fun digest(valor: String) = MessageDigest.getInstance("SHA-256").digest(valor.toByteArray(Charsets.UTF_8))
}
