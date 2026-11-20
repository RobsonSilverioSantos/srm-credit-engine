package br.com.srm.creditengine

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
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
    fun `inicia o backend e publica somente o estado de saude`() {
        val health = get("/actuator/health")
        assertThat(health.statusCode()).isEqualTo(200)
        assertThat(health.body()).isEqualTo("""{"status":"UP"}""")
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401)
        assertThat(get("/api/v1/cedentes").statusCode()).isEqualTo(401)
        assertThat(context.getBeansOfType(Cadastro::class.java)).isEmpty()
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun jwtProperties(registry: DynamicPropertyRegistry) {
            registry.add("security.jwt.secret") { TestJwtSecret.value }
        }
    }
}
