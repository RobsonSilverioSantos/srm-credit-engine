package br.com.srm.creditengine.taxas.adaptador.saida

import br.com.srm.creditengine.liquidacao.dominio.ConflitoLiquidacao
import br.com.srm.creditengine.taxas.dominio.TaxasParaSnapshot
import br.com.srm.creditengine.taxas.dominio.FonteCambial
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoCambio
import java.time.Duration
import java.time.Clock
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

class ValidadorCambioJdbc(
    private val jdbc: JdbcTemplate,
    private val transacao: TransactionTemplate,
    private val relogio: Clock,
    private val historicoConfigurado: HistoricoCambio? = null,
) {
    fun validar(org: UUID, taxas: TaxasParaSnapshot) {
        val cambio = taxas.cambio ?: return
        check(TransactionSynchronizationManager.isActualTransactionActive())
        jdbc.queryForObject("SELECT pg_advisory_xact_lock_shared(hashtextextended(CAST(? AS text), 26))",
            Any::class.java, org.toString())
        jdbc.execute("SELECT pg_advisory_xact_lock_shared(20261122, 26)")
        val agora = relogio.instant().truncatedTo(ChronoUnit.MICROS)
        cambio.validar(agora)
        val historico = historicoConfigurado ?: HistoricoCambioJdbc(jdbc, transacao, relogio,
            modoDemo = cambio.cotacao.fonte == FonteCambial.DEMO,
            validadeDemo = Duration.between(cambio.cotacao.vigenteEm, cambio.validaAte))
        val atual = historico
            .consultar(org, agora)
        if (atual.cotacao.id != cambio.cotacao.id || atual.politicaId != cambio.politicaId ||
            atual.selecaoId != cambio.selecaoId) {
            throw ConflitoLiquidacao("Política/cotação alterada; obtenha nova prévia")
        }
    }
}
