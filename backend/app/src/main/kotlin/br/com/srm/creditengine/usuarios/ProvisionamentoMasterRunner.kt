package br.com.srm.creditengine.usuarios

import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.ProvisionarMaster
import java.util.UUID
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ProvisionamentoMasterRunner {
    @Bean
    @ConditionalOnProperty(
        name = ["srm.security.bootstrap-master.enabled"],
        havingValue = "true",
    )
    fun provisionarMasterRunner(provisionarMaster: ProvisionarMaster) = ApplicationRunner {
        val console = System.console()
            ?: error("O provisionamento MASTER exige um terminal interativo")
        val organizacaoId = UUID.fromString(console.readLine("Organização UUID: "))
        val email = console.readLine("E-mail do MASTER: ")
        val senha = console.readPassword("Senha (12 a 72 bytes UTF-8): ")
            ?: error("Não foi possível ler a senha")
        val confirmacao = console.readPassword("Confirme a senha: ")
            ?: error("Não foi possível ler a confirmação")

        try {
            require(senha.contentEquals(confirmacao)) { "As senhas não coincidem" }
            provisionarMaster.executar(organizacaoId, email, String(senha))
            console.printf("MASTER provisionado. A senha não foi exibida nem registrada.%n")
        } finally {
            senha.fill('\u0000')
            confirmacao.fill('\u0000')
        }
    }
}
