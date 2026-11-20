package br.com.srm.creditengine.cadastro

import java.util.UUID
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate

@Configuration(proxyBeanMethods = false)
class CadastroConfiguracao {
    @Bean
    @Profile("demo")
    @ConditionalOnProperty(prefix = "srm.demo", name = ["bootstrap-organizacao"], havingValue = "true")
    fun bootstrapOrganizacao(jdbc: JdbcTemplate): CommandLineRunner = CommandLineRunner {
        jdbc.update(
            "INSERT INTO organizacoes (id, nome) VALUES (?, ?) ON CONFLICT (id) DO NOTHING",
            UUID.fromString("00000000-0000-4000-8000-000000000001"), "Organização demonstrativa",
        )
    }
}
