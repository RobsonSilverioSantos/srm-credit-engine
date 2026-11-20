package br.com.srm.creditengine.precificacao

import br.com.srm.creditengine.precificacao.dominio.CalculoValorPresente
import br.com.srm.creditengine.precificacao.dominio.ChequePreDatado
import br.com.srm.creditengine.precificacao.dominio.DadosPrecificacao
import br.com.srm.creditengine.precificacao.dominio.DuplicataMercantil
import br.com.srm.creditengine.precificacao.dominio.EstrategiaPrecificacao
import br.com.srm.creditengine.precificacao.dominio.Modalidades
import br.com.srm.creditengine.precificacao.dominio.MotorPrecificacao
import java.math.BigDecimal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PrecificacaoTest {
    private val motor = MotorPrecificacao()
    private val c1 = DadosPrecificacao(
        modalidade = Modalidades.DUPLICATA_MERCANTIL,
        valorFace = BigDecimal("100000.00"),
        taxaBase = BigDecimal("0.01"),
        prazoMeses = 3,
    )

    @Test
    fun `C1 duplicata em BRL`() {
        val resultado = motor.precificar(c1)
        assertThat(resultado.valorPresenteBrl).isEqualTo(BigDecimal("92859.94"))
        assertThat(resultado.desagioBrl).isEqualTo(BigDecimal("7140.06"))
        assertThat(resultado.valorPresenteUsd).isNull()
    }

    @Test
    fun `C2 cheque em BRL`() {
        val resultado = motor.precificar(
            c1.copy(modalidade = Modalidades.CHEQUE_PRE_DATADO, valorFace = BigDecimal("25000.00"), prazoMeses = 2),
        )
        assertThat(resultado.valorPresenteBrl).isEqualTo(BigDecimal("23337.77"))
        assertThat(resultado.desagioBrl).isEqualTo(BigDecimal("1662.23"))
    }

    @Test
    fun `C3 converte VP em BRL já arredondado`() {
        val resultado = motor.precificar(c1.copy(cotacaoBrlPorUsd = BigDecimal("5.4321")))
        assertThat(resultado.valorPresenteBrl).isEqualTo(BigDecimal("92859.94"))
        assertThat(resultado.desagioBrl).isEqualTo(BigDecimal("7140.06"))
        assertThat(resultado.valorPresenteUsd).isEqualTo(BigDecimal("17094.67"))
    }

    @Test
    fun `rejeita entradas financeiras inválidas`() {
        listOf(0, -1).forEach { prazo ->
            assertThrows<IllegalArgumentException> { motor.precificar(c1.copy(prazoMeses = prazo)) }
        }
        listOf("0", "-1", "1.001").forEach { face ->
            assertThrows<IllegalArgumentException> { motor.precificar(c1.copy(valorFace = BigDecimal(face))) }
        }
        assertThrows<IllegalArgumentException> { motor.precificar(c1.copy(taxaBase = BigDecimal("-0.01"))) }
        listOf("0", "-5.4321").forEach { cotacao ->
            assertThrows<IllegalArgumentException> { motor.precificar(c1.copy(cotacaoBrlPorUsd = BigDecimal(cotacao))) }
        }
        assertThrows<IllegalArgumentException> { motor.precificar(c1.copy(modalidade = "DESCONHECIDA")) }
    }

    @Test
    fun `arredonda empates HALF_EVEN em BRL e USD`() {
        val semSpread = object : EstrategiaPrecificacao {
            override val modalidade = "SEM_SPREAD"
            override fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal =
                CalculoValorPresente.calcular(dados, BigDecimal.ZERO)
        }
        val motorTeste = MotorPrecificacao(listOf(semSpread))
        val dados = c1.copy(modalidade = semSpread.modalidade, taxaBase = BigDecimal.ONE, prazoMeses = 1)
        assertThat(motorTeste.precificar(dados.copy(valorFace = BigDecimal("1.01"))).valorPresenteBrl)
            .isEqualTo(BigDecimal("0.50"))
        assertThat(motorTeste.precificar(dados.copy(valorFace = BigDecimal("1.03"))).valorPresenteBrl)
            .isEqualTo(BigDecimal("0.52"))
        val usd = dados.copy(taxaBase = BigDecimal.ZERO, cotacaoBrlPorUsd = BigDecimal("4"))
        assertThat(motorTeste.precificar(usd.copy(valorFace = BigDecimal("0.50"))).valorPresenteUsd)
            .isEqualTo(BigDecimal("0.12"))
        assertThat(motorTeste.precificar(usd.copy(valorFace = BigDecimal("0.70"))).valorPresenteUsd)
            .isEqualTo(BigDecimal("0.18"))
        val arredondaAntesDeConverter = dados.copy(valorFace = BigDecimal("1.01"), cotacaoBrlPorUsd = BigDecimal("0.1"))
        assertThat(motorTeste.precificar(arredondaAntesDeConverter).valorPresenteUsd)
            .isEqualTo(BigDecimal("5.00"))
    }

    @Test
    fun `não arredonda o cálculo a cada mês`() {
        val resultado = motor.precificar(c1.copy(valorFace = BigDecimal("0.08")))
        assertThat(resultado.valorPresenteBrl).isEqualTo(BigDecimal("0.07"))
        assertThat(resultado.desagioBrl).isEqualTo(BigDecimal("0.01"))
    }

    @Test
    fun `inclui modalidade sem alterar o motor e rejeita registros duplicados`() {
        val novaModalidade = object : EstrategiaPrecificacao {
            override val modalidade = "NOVA_MODALIDADE"
            override fun calcularValorPresente(dados: DadosPrecificacao): BigDecimal =
                CalculoValorPresente.calcular(dados, BigDecimal("0.05"))
        }
        val extensivel = MotorPrecificacao(listOf(DuplicataMercantil, ChequePreDatado, novaModalidade))
        assertThat(extensivel.precificar(c1.copy(modalidade = novaModalidade.modalidade)).valorPresenteBrl)
            .isEqualTo(BigDecimal("83961.93"))
        assertThat(extensivel.precificar(c1).valorPresenteBrl).isEqualTo(BigDecimal("92859.94"))
        assertThrows<IllegalArgumentException> { MotorPrecificacao(listOf(novaModalidade, novaModalidade)) }
    }
}
