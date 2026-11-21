package br.com.srm.creditengine.liquidacao

import br.com.srm.creditengine.liquidacao.adaptador.saida.MetricasLiquidacao
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class MetricasLiquidacaoTest {
    @Test
    fun `registra metricas sem labels de alta cardinalidade e mede falhas`() {
        val registry = SimpleMeterRegistry()
        val metricas = MetricasLiquidacao(registry)

        metricas.registrarConfirmacao()
        metricas.registrarReplay()
        assertThrows<IllegalStateException> {
            metricas.medir<Int> { throw IllegalStateException("falha controlada") }
        }

        val confirmadas = registry.get("srm.liquidacoes.confirmadas").counter()
        val replays = registry.get("srm.liquidacoes.replays").counter()
        assertThat(confirmadas.count()).isEqualTo(1.0)
        assertThat(replays.count()).isEqualTo(1.0)
        assertThat(confirmadas.id.tags).isEmpty()
        assertThat(replays.id.tags).isEmpty()
        val timer = registry.get("srm.liquidacoes.duracao").timer()
        assertThat(timer.count()).isEqualTo(1)
        assertThat(timer.id.tags).isEmpty()
    }
}
