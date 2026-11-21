package br.com.srm.creditengine.cadastro

import br.com.srm.creditengine.cadastro.dominio.DocumentoEmpresarial
import br.com.srm.creditengine.cadastro.dominio.StatusDocumento
import br.com.srm.creditengine.cadastro.dominio.ValidadorCnpj
import br.com.srm.creditengine.cadastro.dominio.chaveComparacaoDocumento
import java.util.Locale
import kotlin.random.Random
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test

class DocumentoEmpresarialTest {
    private val cnpj = ValidadorCnpj()

    @Test
    fun `vetores oficiais validam dois DVs preservando zeros e caixa canonica`() {
        VetoresDocumento.oficiais.forEach { numero ->
            listOf(numero, VetoresDocumento.comMascara(numero), numero.lowercase(Locale.ROOT),
                VetoresDocumento.comMascara(numero).lowercase(Locale.ROOT)).forEach { entrada ->
                val documento = DocumentoEmpresarial.validar("BR", "CNPJ", entrada)
                assertThat(documento.numero).isEqualTo(numero)
                assertThat(documento.status).isEqualTo(StatusDocumento.VALIDADO_DV)
            }
        }
    }

    @Test
    fun `rejeita cada DV adulterado repeticoes e formato que nao seja exato`() {
        VetoresDocumento.oficiais.forEach { numero ->
            (12..13).forEach { posicao ->
                ('0'..'9').filter { it != numero[posicao] }.forEach { adulterado ->
                    val invalido = numero.toCharArray().also { it[posicao] = adulterado }.concatToString()
                    assertThatIllegalArgumentException().isThrownBy { cnpj.normalizarEValidar(invalido) }
                }
            }
        }
        (VetoresDocumento.malformados + ('0'..'9').map { it.toString().repeat(14) } +
            "00.000.000/0000-00").forEach { numero ->
            assertThatIllegalArgumentException().isThrownBy { cnpj.normalizarEValidar(numero) }
        }
    }

    @Test
    fun `sinteticos independentes exercitam todos os restos e posicoes alfanumericas`() {
        val random = Random(14)
        val caracteres = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val restosPrimeiro = mutableSetOf<Int>()
        val restosSegundo = mutableSetOf<Int>()
        repeat(1000) {
            val base = (1..12).map { caracteres[random.nextInt(caracteres.length)] }.joinToString("")
            val numero = VetoresDocumento.sintetico(base)
            restosPrimeiro += base.indices.sumOf { (base[it].code - 48) * listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)[it] } % 11
            val base2 = numero.take(13)
            restosSegundo += base2.indices.sumOf { (base2[it].code - 48) * listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)[it] } % 11
            assertThat(cnpj.normalizarEValidar(numero)).isEqualTo(numero)
        }
        assertThat(restosPrimeiro).containsExactlyInAnyOrderElementsOf(0..10)
        assertThat(restosSegundo).containsExactlyInAnyOrderElementsOf(0..10)
    }

    @Test
    fun `OUTRO tem somente sintaxe ASCII estrita sem DV nem confirmacao fiscal`() {
        Locale.getISOCountries().filter { it != "BR" }.forEach { pais ->
            listOf("A", "0", "A".repeat(32), "00000000000000").forEach { numero ->
                val documento = DocumentoEmpresarial.validar(pais, "OUTRO", numero)
                assertThat(documento.status).isEqualTo(StatusDocumento.NAO_VERIFICADO)
                assertThat(documento.numero).isEqualTo(numero)
            }
            assertThatIllegalArgumentException().isThrownBy { DocumentoEmpresarial.validar("BR", "OUTRO", "ABC") }
        }
        listOf("", "A".repeat(33), "abc", " A", "A ", "A-B", "A.B", "A/B", "A\tB", "A\n", "Á", "Ａ", "K", "ſ", "ß")
            .forEach { numero ->
                assertThatIllegalArgumentException().isThrownBy { DocumentoEmpresarial.validar("US", "OUTRO", numero) }
            }
        listOf("ZZ", "XK", "USA", "us", "", " US", "US ").forEach { pais ->
            assertThatIllegalArgumentException().isThrownBy { DocumentoEmpresarial.validar(pais, "OUTRO", "A") }
        }
        listOf("EIN", "VAT", "", "outro", "CNPJ").forEach { tipo ->
            assertThatIllegalArgumentException().isThrownBy { DocumentoEmpresarial.validar("US", tipo, "123456789") }
        }
    }

    @Test
    fun `valor canonico nao pode ser construido ou copiado com identificacao invalida`() {
        assertThatIllegalArgumentException().isThrownBy { DocumentoEmpresarial("BR", "CNPJ", "12.ABC.345/01DE-35") }
        val documento = DocumentoEmpresarial.validar("BR", "CNPJ", "12ABC34501DE35")
        assertThatIllegalArgumentException().isThrownBy { documento.copy(numero = "12ABC34501DE36") }
        assertThat(chaveComparacaoDocumento(" 12.abc.345/01DE-35 ")).isEqualTo(documento.numero)
        assertThat(chaveComparacaoDocumento("ÁKＡſß а")).isEmpty()
    }
}
