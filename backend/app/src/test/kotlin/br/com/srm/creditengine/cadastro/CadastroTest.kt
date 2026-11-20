package br.com.srm.creditengine.cadastro

import br.com.srm.creditengine.cadastro.aplicacao.ServicoCadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoCedente
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoRecebivel
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Cedentes
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Recebiveis
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import java.math.BigDecimal
import java.util.UUID
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class CadastroTest {
    private val service = ServicoCadastro(ContextoOrganizacao { UUID.randomUUID() }, mock(Cedentes::class.java), mock(Recebiveis::class.java))

    @Test
    fun `rejeita campos e valores invalidos antes de persistir`() {
        listOf("", " ", "x".repeat(161)).forEach { nome ->
            assertThatIllegalArgumentException().isThrownBy { service.cadastrarCedente(NovoCedente(nome, "doc")) }
        }
        listOf("", " ", "x".repeat(33)).forEach { documento ->
            assertThatIllegalArgumentException().isThrownBy { service.cadastrarCedente(NovoCedente("Nome", documento)) }
        }
        val valido = NovoRecebivel(UUID.randomUUID(), Modalidades.DUPLICATA_MERCANTIL, BigDecimal("100.00"), 1)
        listOf(
            valido.copy(modalidade = "OUTRA"),
            valido.copy(valorFaceBrl = BigDecimal.ZERO),
            valido.copy(valorFaceBrl = BigDecimal("-0.01")),
            valido.copy(valorFaceBrl = BigDecimal("0.001")),
            valido.copy(valorFaceBrl = BigDecimal("100000000000000000")),
            valido.copy(prazoMeses = 0),
            valido.copy(prazoMeses = -1),
        ).forEach { comando ->
            assertThatIllegalArgumentException().isThrownBy { service.cadastrarRecebivel(comando) }
        }
    }

    @Test
    fun `falha de identidade nao acessa persistencia`() {
        val cedentes = mock(Cedentes::class.java)
        val recebiveis = mock(Recebiveis::class.java)
        val service = ServicoCadastro(ContextoOrganizacao { error("Identidade indisponível") }, cedentes, recebiveis)
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { service.cadastrarCedente(NovoCedente("Nome", "doc")) }
        assertThatExceptionOfType(IllegalStateException::class.java)
            .isThrownBy { service.listarRecebiveis() }
        verifyNoInteractions(cedentes, recebiveis)
    }
}
