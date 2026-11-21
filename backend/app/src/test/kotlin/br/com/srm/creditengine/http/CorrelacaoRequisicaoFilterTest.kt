package br.com.srm.creditengine.http

import br.com.srm.creditengine.http.adaptador.entrada.CorrelacaoRequisicaoFilter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelacaoRequisicaoFilterTest {
    @Test
    fun `correlaciona o processamento e limpa o MDC ao concluir`() {
        val request = MockHttpServletRequest("GET", "/actuator/health")
        val response = MockHttpServletResponse()
        var idObservado: String? = null

        CorrelacaoRequisicaoFilter().doFilter(request, response) { _, _ ->
            idObservado = MDC.get("correlationId")
        }

        assertThat(idObservado).isEqualTo(response.getHeader("X-Correlation-Id"))
        assertThat(idObservado).matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        assertThat(MDC.get("correlationId")).isNull()
    }
}
