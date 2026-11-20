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
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"],
)
class CreditEngineApplicationTest {
    @Autowired
    private lateinit var context: ApplicationContext
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `inicia o backend e publica somente o estado de saude`() {
        val health = get("/actuator/health")
        assertThat(health.statusCode()).isEqualTo(200)
        assertThat(health.body()).isEqualTo("""{"status":"UP"}""")
        assertThat(get("/actuator/env").statusCode()).isEqualTo(404)
        assertThat(get("/api/v1/cedentes").statusCode()).isEqualTo(404)
        assertThat(context.getBeansOfType(Cadastro::class.java)).isEmpty()
    }

    private fun get(path: String): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())
    }
}
