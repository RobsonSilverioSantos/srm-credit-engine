package br.com.srm.creditengine.http.adaptador.entrada

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelacaoRequisicaoFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(CorrelacaoRequisicaoFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId = UUID.randomUUID().toString()
        val anterior = MDC.get("correlationId")
        val inicioNanos = System.nanoTime()
        response.setHeader("X-Correlation-Id", correlationId)
        MDC.put("correlationId", correlationId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            try {
                log.atInfo()
                    .addKeyValue("event", "http.request.completed")
                    .addKeyValue("method", request.method)
                    .addKeyValue("durationNanos", System.nanoTime() - inicioNanos)
                    .log("HTTP request completed")
            } finally {
                if (anterior == null) MDC.remove("correlationId") else MDC.put("correlationId", anterior)
            }
        }
    }
}
