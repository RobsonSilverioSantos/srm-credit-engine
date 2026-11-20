package br.com.srm.creditengine.taxas.adaptador.saida

import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoFinanceiroPorta
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.HistoricoElegivel
import br.com.srm.creditengine.taxas.dominio.SpreadRecebivel
import br.com.srm.creditengine.taxas.dominio.TaxaBase
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import org.springframework.dao.TransientDataAccessException
import org.springframework.transaction.support.TransactionSynchronizationManager

class ConsultaCambialIndisponivel(message: String, cause: Throwable) : IllegalStateException(message, cause)

class HistoricoCambialResiliente(
    private val historico: HistoricoFinanceiroPorta,
    private val timeout: Duration,
    private val tentativas: Int,
    private val atraso: Duration,
) : HistoricoFinanceiroPorta, AutoCloseable {
    init {
        require(timeout >= Duration.ofMillis(1) && timeout <= Duration.ofSeconds(30) && timeout.nano % 1_000_000 == 0) {
            "Timeout cambial deve estar entre 1 ms e 30 s, em milissegundos"
        }
        require(tentativas in 1..5) { "Tentativas cambiais devem estar entre 1 e 5" }
        require(atraso >= Duration.ZERO && atraso <= Duration.ofSeconds(5) && atraso.nano % 1_000_000 == 0) {
            "Atraso cambial deve estar entre 0 e 5 s, em milissegundos"
        }
    }

    private val executor = ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(4)) { tarefa ->
        Thread(tarefa, "consulta-cambial").apply { isDaemon = true }
    }

    override fun incluir(taxa: TaxaBase) = historico.incluir(taxa)
    override fun incluir(spread: SpreadRecebivel) = historico.incluir(spread)
    override fun incluir(cotacao: CotacaoCambial) = historico.incluir(cotacao)

    override fun consultar(modalidade: String, referencia: Instant, incluirCotacao: Boolean): HistoricoElegivel {
        if (!incluirCotacao) return historico.consultar(modalidade, referencia, false)
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "Consulta cambial resiliente deve ocorrer fora da transação financeira"
        }
        for (tentativa in 1..tentativas) {
            val falha = try {
                return consultarUmaVez(modalidade, referencia)
            } catch (e: TimeoutException) {
                e
            } catch (e: TransientDataAccessException) {
                e
            }
            if (tentativa == tentativas) {
                throw ConsultaCambialIndisponivel("Consulta cambial indisponível após $tentativas tentativa(s)", falha)
            }
            try {
                Thread.sleep(atraso.toMillis())
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw ConsultaCambialIndisponivel("Consulta cambial interrompida durante espera", e)
            }
        }
        error("Número de tentativas inválido")
    }

    private fun consultarUmaVez(modalidade: String, referencia: Instant): HistoricoElegivel {
        val futuro = FutureTask { historico.consultar(modalidade, referencia, true) }
        try {
            executor.execute(futuro)
        } catch (e: RejectedExecutionException) {
            throw ConsultaCambialIndisponivel("Capacidade de consultas cambiais esgotada", e)
        }
        try {
            return futuro.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            futuro.cancel(true)
            executor.remove(futuro)
            throw e
        } catch (e: InterruptedException) {
            futuro.cancel(true)
            executor.remove(futuro)
            Thread.currentThread().interrupt()
            throw ConsultaCambialIndisponivel("Consulta cambial interrompida", e)
        } catch (e: ExecutionException) {
            when (val causa = e.cause) {
                is RuntimeException -> throw causa
                is Error -> throw causa
                else -> throw IllegalStateException("Falha na consulta cambial", causa)
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }
}
