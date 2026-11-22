package br.com.srm.creditengine.liquidacao.aplicacao

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Recebiveis
import br.com.srm.creditengine.cadastro.dominio.StatusRecebivel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ComandoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.LiquidarRecebivel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.ResultadoOperacaoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdempotenciaLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.TransacaoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.liquidacao.dominio.Liquidacao
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.liquidacao.dominio.RecebivelNaoEncontrado
import br.com.srm.creditengine.precificacao.aplicacao.precificarComTaxas
import br.com.srm.creditengine.precificacao.aplicacao.RevisaoFinanceira
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import br.com.srm.creditengine.taxas.dominio.FonteCambial
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID

class ServicoLiquidacao(
    private val identidade: IdentidadeConfiavel,
    private val recebiveis: Recebiveis,
    private val idempotencia: IdempotenciaLiquidacao,
    private val taxas: TaxasPorta,
    private val transacao: TransacaoLiquidacao,
    private val relogio: Clock,
) : LiquidarRecebivel {
    override fun liquidar(comando: ComandoLiquidacao): ResultadoOperacaoLiquidacao {
        val usuario = identidade.identidade()
        val hash = MessageDigest.getInstance("SHA-256").digest(
            ("LIQUIDAR_RECEBIVEL\n${comando.recebivelId}\n${comando.moeda.name}" +
                (comando.revisaoCambial?.let { "\n$it" } ?: "")).toByteArray(StandardCharsets.UTF_8),
        )
        idempotencia.consultar(usuario.organizacaoId, comando.chaveIdempotencia, hash)?.let { return it }
        val recebivel = recebiveis.buscar(usuario.organizacaoId, comando.recebivelId)
            ?: throw RecebivelNaoEncontrado()
        if (recebivel.status != StatusRecebivel.DISPONIVEL) {
            idempotencia.consultar(usuario.organizacaoId, comando.chaveIdempotencia, hash)?.let { return it }
            throw ConflitoLiquidacao("Recebível já liquidado")
        }
        val referencia = relogio.instant().truncatedTo(ChronoUnit.MICROS)
        val snapshot = taxas.consultarEm(recebivel.modalidade, comando.moeda == MoedaPagamento.USD, referencia)
        val cambio = snapshot.cambio
        val resultado = precificarComTaxas(recebivel.modalidade, recebivel.valorFaceBrl, recebivel.prazoMeses, snapshot)
        if (comando.revisaoCambial != null || (cambio != null && cambio.cotacao.fonte != FonteCambial.DEMO)) {
            val revisao = RevisaoFinanceira.calcular(recebivel.modalidade, recebivel.valorFaceBrl,
                recebivel.prazoMeses, comando.moeda, snapshot, resultado)
            if (revisao != comando.revisaoCambial) {
                throw ConflitoLiquidacao("Prévia financeira ausente ou obsoleta; obtenha nova prévia")
            }
        }
        val pagamento = if (comando.moeda == MoedaPagamento.USD) checkNotNull(resultado.valorPresenteUsd)
        else resultado.valorPresenteBrl
        val liquidacao = Liquidacao(UUID.randomUUID(), recebivel, snapshot, resultado, comando.moeda, pagamento)
        return transacao.confirmar(usuario, comando.chaveIdempotencia, hash, liquidacao)
    }
}
