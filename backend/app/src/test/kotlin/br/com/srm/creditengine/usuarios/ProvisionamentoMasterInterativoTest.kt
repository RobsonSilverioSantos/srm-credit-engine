package br.com.srm.creditengine.usuarios

import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.ProvisionarMaster
import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProvisionamentoMasterInterativoTest {
    private val organizacao = UUID.fromString("00000000-0000-4000-8000-000000000001")

    @Test
    fun `reexibe as perguntas para uuid email senha e confirmacao invalidos sem provisionar antecipadamente`() {
        val curta = "12345678901".toCharArray()
        val longa = "X".repeat(73).toCharArray()
        val senhaErrada = "SenhaValida456!".toCharArray()
        val confirmacaoErrada = "SenhaDiferente456!".toCharArray()
        val senhaCorreta = "SenhaValida456!".toCharArray()
        val confirmacaoCorreta = "SenhaValida456!".toCharArray()
        val terminal = TerminalFalso(
            textos = listOf("identificador-invalido", organizacao.toString(), "email-invalido", " Master@Example.Invalid "),
            senhas = listOf(curta, longa, senhaErrada, confirmacaoErrada, senhaCorreta, confirmacaoCorreta),
        )
        var chamadas = 0
        val provisionador = ProvisionarMaster { organizacaoRecebida, email, senha ->
            chamadas++
            assertThat(organizacaoRecebida).isEqualTo(organizacao)
            assertThat(email).isEqualTo("master@example.invalid")
            assertThat(senha).isEqualTo("SenhaValida456!")
            usuario(email)
        }

        ProvisionamentoMasterInterativo(terminal, provisionador).executar()

        assertThat(chamadas).isEqualTo(1)
        assertThat(terminal.mensagens).anyMatch { it.startsWith("UUID inválido") }
        assertThat(terminal.mensagens).anyMatch { it.startsWith("E-mail inválido") }
        assertThat(terminal.mensagens.count { it.startsWith("Senha inválida") }).isEqualTo(2)
        assertThat(terminal.mensagens).anyMatch { it.startsWith("As senhas não coincidem") }
        assertThat(terminal.mensagens).contains("MASTER provisionado. A senha não foi exibida nem registrada.")
        assertThat(terminal.mensagens.joinToString(" ")).doesNotContain("SenhaValida456!", "SenhaDiferente456!")
        listOf(curta, longa, senhaErrada, confirmacaoErrada, senhaCorreta, confirmacaoCorreta)
            .forEach { assertThat(it).containsOnly('\u0000') }
    }

    @Test
    fun `valida tamanho em bytes UTF8 e aceita senha com caracteres multibyte`() {
        val senha = "áááááá".toCharArray() // 6 caracteres, 12 bytes UTF-8.
        val confirmacao = "áááááá".toCharArray()
        val terminal = TerminalFalso(
            textos = listOf(organizacao.toString(), "master@example.invalid"),
            senhas = listOf(senha, confirmacao),
        )
        var executado = false
        val provisionador = ProvisionarMaster { _, email, senhaRecebida ->
            executado = true
            assertThat(senhaRecebida).isEqualTo("áááááá")
            usuario(email)
        }

        ProvisionamentoMasterInterativo(terminal, provisionador).executar()

        assertThat(executado).isTrue()
        assertThat(terminal.mensagens).doesNotContain("Senha inválida: informe de 12 a 72 bytes UTF-8. Tente novamente.")
        assertThat(senha).containsOnly('\u0000')
        assertThat(confirmacao).containsOnly('\u0000')
    }

    private fun usuario(email: String): UsuarioOrganizacional =
        UsuarioOrganizacional(UUID.randomUUID(), organizacao, email, PerfilUsuario.MASTER, true)

    private class TerminalFalso(
        textos: List<String>,
        senhas: List<CharArray>,
    ) : TerminalProvisionamentoMaster {
        private val textosPendentes = ArrayDeque(textos)
        private val senhasPendentes = ArrayDeque(senhas)
        val mensagens = mutableListOf<String>()

        override fun lerTexto(pergunta: String): String? =
            if (textosPendentes.isEmpty()) null else textosPendentes.removeFirst()

        override fun lerSenha(pergunta: String): CharArray? =
            if (senhasPendentes.isEmpty()) null else senhasPendentes.removeFirst()

        override fun informar(mensagem: String) {
            mensagens += mensagem
        }
    }
}
