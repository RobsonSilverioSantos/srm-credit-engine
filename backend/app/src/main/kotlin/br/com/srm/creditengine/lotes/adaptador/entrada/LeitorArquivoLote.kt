package br.com.srm.creditengine.lotes.adaptador.entrada

import br.com.srm.creditengine.lotes.aplicacao.LinhaImportacao
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

class LeitorArquivoLote(private val maxLinhas: Int, private val maxBytes: Int) {
    init {
        require(maxLinhas in 1..10_000 && maxBytes in 1..16 * 1024 * 1024)
    }

    fun ler(nome: String?, tamanho: Long, input: InputStream): List<LinhaImportacao> {
        if (tamanho > maxBytes) throw ArquivoLoteExcessivo()
        val bytes = input.use { it.readNBytes(maxBytes + 1) }
        if (bytes.size > maxBytes) throw ArquivoLoteExcessivo()
        val linhas = when {
            nome?.endsWith(".csv", ignoreCase = true) == true -> lerCsv(bytes)
            nome?.endsWith(".xlsx", ignoreCase = true) == true -> lerPlanilhaPadronizada(bytes, maxBytes, maxLinhas)
            else -> throw IllegalArgumentException("Formato de arquivo não suportado.")
        }
        require(linhas.size in 2..(maxLinhas + 1) && linhas.first() == CABECALHO) { "Cabeçalho ou quantidade de dados inválidos." }
        return linhas.drop(1).mapIndexed { i, valores -> interpretarLinhaLote(i + 2, valores) }
    }

    private fun lerCsv(bytes: ByteArray): List<List<String>> {
        val texto = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw IllegalArgumentException("O arquivo deve ser UTF-8.")
        }
        return texto.removePrefix("\uFEFF").removeSuffix("\r\n").removeSuffix("\n").removeSuffix("\r")
            .lineSequence().take(maxLinhas + 2).map(::separarCsv).toList()
    }

    private fun separarCsv(linha: String): List<String> {
        val campos = mutableListOf<String>()
        var atual = StringBuilder()
        var entreAspas = false
        var fechado = false
        var i = 0
        while (i < linha.length) {
            val c = linha[i++]
            when {
                entreAspas && c == '"' && i < linha.length && linha[i] == '"' -> {
                    atual.append('"')
                    i++
                }
                entreAspas && c == '"' -> {
                    entreAspas = false
                    fechado = true
                }
                entreAspas -> atual.append(c)
                c == ';' -> {
                    campos.add(atual.toString())
                    if (campos.size >= CABECALHO.size) return emptyList()
                    atual = StringBuilder()
                    fechado = false
                }
                !fechado && atual.isEmpty() && c == '"' -> entreAspas = true
                fechado || c == '"' -> return emptyList()
                else -> atual.append(c)
            }
        }
        if (entreAspas) return emptyList()
        return campos + atual.toString()
    }

    companion object {
        internal val CABECALHO = listOf(
            "referenciaExterna", "cedenteId", "modalidade", "valorFaceBrl", "prazoMeses", "vencimento", "moedaPagamento",
        )
    }
}
