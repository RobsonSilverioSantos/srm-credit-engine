package br.com.srm.creditengine

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.mockito.Mockito
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "srm.taxas.enabled=false",
    ],
)
@Import(CreditEngineApplicationTest.DatabaseMock::class)
@ExtendWith(OutputCaptureExtension::class)
class CreditEngineApplicationTest {
    @TestConfiguration(proxyBeanMethods = false)
    class DatabaseMock {
        @Bean
        fun jdbcTemplate(): JdbcTemplate = Mockito.mock(JdbcTemplate::class.java)
    }

    @Autowired
    private lateinit var context: ApplicationContext
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `publica health e protege as metricas com autenticacao`() {
        val health = get("/actuator/health")
        assertThat(health.statusCode()).isEqualTo(200)
        assertThat(health.body()).isEqualTo("""{"status":"UP"}""")
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401)
        assertThat(get("/actuator/metrics/srm.liquidacoes.confirmadas").statusCode()).isEqualTo(401)
        assertThat(get("/api/v1/cedentes").statusCode()).isEqualTo(401)
        assertThat(context.getBeansOfType(Cadastro::class.java)).isEmpty()
    }

    @Test
    fun `gera correlacao por requisicao e inclui no log estruturado`(output: CapturedOutput) {
        val response = get("/actuator/health", "id-controlado-pelo-cliente")
        val correlationId = response.headers().firstValue("X-Correlation-Id").orElseThrow()

        assertThat(correlationId).matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
        assertThat(correlationId).isNotEqualTo("id-controlado-pelo-cliente")
        assertThat(output.toString()).contains("\"correlationId\":\"$correlationId\"")
        assertThat(output.toString()).contains("\"event\":\"http.request.completed\"")
        assertThat(output.toString()).doesNotContain("id-controlado-pelo-cliente")
    }

    private fun get(path: String, correlationId: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(5))
            .GET()
        if (correlationId != null) builder.header("X-Correlation-Id", correlationId)
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { TestJwtSecret.value }
        }
    }
}
