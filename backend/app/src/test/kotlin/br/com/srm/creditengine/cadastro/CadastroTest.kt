package br.com.srm.creditengine.cadastro

import br.com.srm.creditengine.cadastro.aplicacao.ServicoCadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoCedente
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoRecebivel
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Cedentes
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Recebiveis
import br.com.srm.creditengine.cadastro.dominio.Cedente
import br.com.srm.creditengine.cadastro.dominio.StatusDocumento
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import java.math.BigDecimal
import java.util.UUID
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions

class CadastroTest {
    private val service = ServicoCadastro(ContextoOrganizacao { UUID.randomUUID() }, mock(Cedentes::class.java), mock(Recebiveis::class.java))

    @Test
    fun `rejeita campos e valores invalidos antes de persistir`() {
        listOf("", " ", "x".repeat(161)).forEach { nome ->
            assertThatIllegalArgumentException().isThrownBy { service.cadastrarCedente(NovoCedente(nome, "12ABC34501DE35")) }
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
    fun `novas entradas antigas sao BR CNPJ e campos opcionais exigem par valido`() {
        val org = UUID.randomUUID()
        val cedentes = mock(Cedentes::class.java) { chamada ->
            if (chamada.method.name == "cadastrar") chamada.getArgument<Cedente>(0) else null
        }
        val recebiveis = mock(Recebiveis::class.java)
        val service = ServicoCadastro(ContextoOrganizacao { org }, cedentes, recebiveis)
        listOf(
            NovoCedente("Teste", "doc"),
            NovoCedente("Teste", "12ABC34501DE35", pais = "BR"),
            NovoCedente("Teste", "12ABC34501DE35", tipoDocumento = "CNPJ"),
            NovoCedente("Teste", "ABC", "ZZ", "OUTRO"),
            NovoCedente("Teste", "12ABC34501DE35", "US", "CNPJ"),
        ).forEach { comando ->
            assertThatIllegalArgumentException().isThrownBy { service.cadastrarCedente(comando) }
        }
        verifyNoInteractions(cedentes, recebiveis)
        val antigo = service.cadastrarCedente(NovoCedente("Teste", "12.abc.345/01de-35"))
        assertThat(antigo.organizacaoId).isEqualTo(org)
        assertThat(antigo.documento).isEqualTo("12ABC34501DE35")
        val identificacao = requireNotNull(antigo.identificacao)
        assertThat(identificacao.pais).isEqualTo("BR")
        assertThat(identificacao.status).isEqualTo(StatusDocumento.VALIDADO_DV)
        val outro = service.cadastrarCedente(NovoCedente("Teste", "00000000000000", "US", "OUTRO"))
        assertThat(requireNotNull(outro.identificacao).status).isEqualTo(StatusDocumento.NAO_VERIFICADO)
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
