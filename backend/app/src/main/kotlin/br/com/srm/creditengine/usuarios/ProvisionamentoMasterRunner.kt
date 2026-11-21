package br.com.srm.creditengine.usuarios

import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.ProvisionarMaster
import java.io.Console
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * O modo de provisionamento é exclusivamente interativo: nenhuma senha pode ser
 * informada via argumentos, variáveis de ambiente ou arquivos de configuração.
 */
internal interface TerminalProvisionamentoMaster {
    fun lerTexto(pergunta: String): String?
    fun lerSenha(pergunta: String): CharArray?
    fun informar(mensagem: String)
}

private class ConsoleProvisionamentoMaster(private val console: Console) : TerminalProvisionamentoMaster {
    override fun lerTexto(pergunta: String): String? = console.readLine("%s", pergunta)
    override fun lerSenha(pergunta: String): CharArray? = console.readPassword("%s", pergunta)
    override fun informar(mensagem: String) {
        console.printf("%s%n", mensagem)
    }
}

internal class ProvisionamentoMasterInterativo(
    private val terminal: TerminalProvisionamentoMaster,
    private val provisionarMaster: ProvisionarMaster,
) {
    fun executar() {
        val organizacaoId = lerOrganizacao()
        val email = lerEmail()

        while (true) {
            val senha = terminal.lerSenha("Senha (12 a 72 bytes UTF-8): ")
                ?: error("Entrada encerrada antes do provisionamento")
            try {
                val quantidadeBytes = String(senha).toByteArray(StandardCharsets.UTF_8).size
                if (quantidadeBytes !in 12..72) {
                    terminal.informar("Senha inválida: informe de 12 a 72 bytes UTF-8. Tente novamente.")
                    continue
                }

                val confirmacao = terminal.lerSenha("Confirme a senha: ")
                    ?: error("Entrada encerrada antes do provisionamento")
                try {
                    if (!senha.contentEquals(confirmacao)) {
                        terminal.informar("As senhas não coincidem. Digite as duas novamente.")
                        continue
                    }
                    provisionarMaster.executar(organizacaoId, email, String(senha))
                    terminal.informar("MASTER provisionado. A senha não foi exibida nem registrada.")
                    return
                } finally {
                    confirmacao.fill('\u0000')
                }
            } finally {
                senha.fill('\u0000')
            }
        }
    }

    private fun lerOrganizacao(): UUID {
        while (true) {
            val texto = terminal.lerTexto("Organização UUID: ")
                ?: error("Entrada encerrada antes do provisionamento")
            val uuid = runCatching { UUID.fromString(texto.trim()) }.getOrNull()
            if (uuid != null) return uuid
            terminal.informar("UUID inválido. Informe o identificador completo da organização.")
        }
    }

    private fun lerEmail(): String {
        while (true) {
            val email = terminal.lerTexto("E-mail do MASTER: ")
                ?.trim()
                ?.lowercase(Locale.ROOT)
                ?: error("Entrada encerrada antes do provisionamento")
            if (email.length <= 254 && email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
                return email
            }
            terminal.informar("E-mail inválido. Informe um endereço no formato usuario@dominio.")
        }
    }
}

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
        ProvisionamentoMasterInterativo(
            ConsoleProvisionamentoMaster(console),
            provisionarMaster,
        ).executar()
    }
}
