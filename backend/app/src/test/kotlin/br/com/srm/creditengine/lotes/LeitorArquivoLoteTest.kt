package br.com.srm.creditengine.lotes

import br.com.srm.creditengine.lotes.adaptador.entrada.ArquivoLoteExcessivo
import br.com.srm.creditengine.lotes.adaptador.entrada.LeitorArquivoLote
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LeitorArquivoLoteTest {
    private val leitor = LeitorArquivoLote(200, 2 * 1024 * 1024)
    private val cabecalho = listOf("referenciaExterna", "cedenteId", "modalidade", "valorFaceBrl", "prazoMeses", "vencimento", "moedaPagamento")
    private val linha = listOf("N-1", "00000000-0000-4000-8000-000000000001", "DUPLICATA_MERCANTIL", "10000.00", "3", "", "USD")

    @Test
    fun `CSV UTF8 com BOM e XLSX padronizado produzem o mesmo comando`() {
        val csv = ("\uFEFF" + cabecalho.joinToString(";") + "\r\n" + linha.joinToString(";") + "\r\n").toByteArray()
        val a = ler("modelo.csv", csv)
        val b = ler("modelo.xlsx", planilha())
        assertThat(a).isEqualTo(b)
        assertThat(a.single().linha).isEqualTo(2)
        assertThat(a.single().item?.valorFaceBrl?.toPlainString()).isEqualTo("10000.00")
    }

    @Test
    fun `rejeita tamanho encoding formulas DOCTYPE e relacionamentos externos`() {
        val grande = ByteArray(100)
        assertThrows<ArquivoLoteExcessivo> { LeitorArquivoLote(2, 99).ler("grande.csv", grande.size.toLong(), grande.inputStream()) }
        assertThrows<IllegalArgumentException> { ler("invalido.csv", byteArrayOf(0xc3.toByte(), 0x28)) }
        assertThrows<IllegalArgumentException> { ler("formula.xlsx", planilha(formula = true)) }
        assertThrows<IllegalArgumentException> { ler("doctype.xlsx", planilha(doctype = true)) }
        assertThrows<IllegalArgumentException> { ler("externo.xlsx", planilha(externo = true)) }
    }

    @Test
    fun `preserva numero e campo invalido sem alterar os demais comandos`() {
        val csv = listOf(cabecalho, linha, linha.toMutableList().apply { this[3] = "9,99" })
            .joinToString("\n") { it.joinToString(";") }
        val resultado = ler("modelo.csv", csv.toByteArray())
        assertThat(resultado.first().item).isNotNull()
        assertThat(resultado.last().linha).isEqualTo(3)
        assertThat(resultado.last().item).isNull()
        assertThat(resultado.last().erros.single().campo).isEqualTo("valorFaceBrl")
    }

    private fun ler(nome: String, bytes: ByteArray) = leitor.ler(nome, bytes.size.toLong(), bytes.inputStream())

    private fun planilha(formula: Boolean = false, doctype: Boolean = false, externo: Boolean = false): ByteArray {
        val saida = ByteArrayOutputStream()
        ZipOutputStream(saida).use { zip ->
            fun parte(nome: String, txt: String) {
                zip.putNextEntry(ZipEntry(nome))
                zip.write(txt.toByteArray())
                zip.closeEntry()
            }
            parte("[Content_Types].xml", "<Types/>")
            parte("xl/workbook.xml", """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets><sheet r:id="rId1"/></sheets></workbook>""")
            val modo = if (externo) """ TargetMode="External" """ else ""
            parte("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Type="test/worksheet"
                Target="worksheets/sheet1.xml" $modo/></Relationships>""")
            val linhas = listOf(cabecalho, linha).mapIndexed { i, valores ->
                val numero = i + 1
                "<row r=\"$numero\">" + valores.mapIndexed { j, valor ->
                    val ref = "${'A' + j}$numero"
                    val calculada = if (formula && i == 1 && j == 3) "<f>1+1</f>" else ""
                    "<c r=\"$ref\" t=\"inlineStr\">$calculada<is><t>$valor</t></is></c>"
                }.joinToString("") + "</row>"
            }.joinToString("")
            val tipo = if (doctype) "<!DOCTYPE worksheet []>" else ""
            parte("xl/worksheets/sheet1.xml", "$tipo<worksheet><sheetData>$linhas</sheetData></worksheet>")
        }
        return saida.toByteArray()
    }
}
