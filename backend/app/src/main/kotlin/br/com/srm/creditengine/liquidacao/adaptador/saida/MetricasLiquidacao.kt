package br.com.srm.creditengine.liquidacao.adaptador.saida

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer

class MetricasLiquidacao(private val registry: MeterRegistry) {
    private val confirmadas = Counter.builder("srm.liquidacoes.confirmadas")
        .description("Total de novas liquidações confirmadas após commit.")
        .register(registry)
    private val replays = Counter.builder("srm.liquidacoes.replays")
        .description("Total de respostas reproduzidas por idempotência.")
        .register(registry)
    private val duracao = Timer.builder("srm.liquidacoes.duracao")
        .description("Duração total das tentativas do caso de uso de liquidação.")
        .register(registry)

    fun registrarConfirmacao() {
        confirmadas.increment()
    }

    fun registrarReplay() {
        replays.increment()
    }

    fun <T> medir(operacao: () -> T): T {
        val amostra = Timer.start(registry)
        return try {
            operacao()
        } finally {
            amostra.stop(duracao)
        }
    }
}
