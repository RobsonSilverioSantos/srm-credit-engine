package br.com.srm.creditengine.usuarios

import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.ProvisionarMaster
import java.io.Console
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * O modo de provisionamento é exclusivamente interativo: nenhuma senha pode ser
 * informada via argumentos, variáveis de ambiente ou arquivos de configuração.
 */
@Configuration(proxyBeanMethods = false)
class ProvisionamentoMasterRunner {
    private class ConsoleProvisionamentoMaster(private val console: Console) : TerminalProvisionamentoMaster {
        override fun lerTexto(pergunta: String): String? = console.readLine("%s", pergunta)
        override fun lerSenha(pergunta: String): CharArray? = console.readPassword("%s", pergunta)
        override fun informar(mensagem: String) {
            console.printf("%s%n", mensagem)
        }
    }

    @Bean
    @ConditionalOnProperty(
        name = ["srm.security.bootstrap-master.enabled"],
        havingValue = "true",
    )
    fun provisionarMasterRunner(provisionarMaster: ProvisionarMaster) = ApplicationRunner {
        val console = System.console()
            ?: error("O provisionamento MASTER exige um terminal interativo")
        ProvisionamentoMasterInterativo(
            ConsoleProvisionamentoMaster(console),
            provisionarMaster,
        ).executar()
    }
}
