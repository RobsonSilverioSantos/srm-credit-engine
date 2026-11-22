package br.com.srm.creditengine.lotes.adaptador.entrada

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler

internal fun lerPlanilhaPadronizada(bytes: ByteArray, maxBytes: Int, maxLinhas: Int): List<List<String>> {
    try {
        val documentos = lerPartes(bytes, maxBytes * 4)
        val tipos = documentos.getValue("[Content_Types].xml")
        val tiposRegistrados = tipos.elementos("Default") + tipos.elementos("Override")
        require(tiposRegistrados.none {
            val modo = it.getAttribute("ContentType").lowercase()
            "macro" in modo || "activex" in modo || "oleobject" in modo
        }) { "Conteúdo ativo não permitido." }
        val primeira = documentos.getValue("xl/workbook.xml").elementos("sheet").firstOrNull()
        val relacionamento = primeira?.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
        val planilha = documentos.getValue("xl/_rels/workbook.xml.rels").elementos("Relationship")
            .singleOrNull { it.getAttribute("Id") == relacionamento && it.getAttribute("Type").endsWith("/worksheet") }
            ?.getAttribute("Target") ?: throw IllegalArgumentException("Planilha inválida.")
        val caminho = if (planilha.startsWith("/")) planilha.removePrefix("/") else "xl/$planilha"
        require(caminho.matches(Regex("xl/worksheets/sheet[1-9][0-9]*\\.xml")))
        // Fórmulas e vínculos são rejeitados em todas as folhas, não apenas na primeira.
        require(documentos.filterKeys { it.startsWith("xl/worksheets/") }.values.none {
            it.elementos("f").isNotEmpty() || it.elementos("externalLink").isNotEmpty()
        })
        val textos = documentos["xl/sharedStrings.xml"]?.elementos("si")?.map {
            it.elementosFilhos("t").joinToString("") { t -> t.textContent }
                .ifEmpty { it.textContent }
        }.orEmpty()
        val linhas = documentos.getValue(caminho).elementos("row")
        require(linhas.size in 2..(maxLinhas + 1))
        return linhas.mapIndexed { indice, linha ->
            require(linha.getAttribute("r").toIntOrNull() == indice + 1)
            val valores = MutableList(LeitorArquivoLote.CABECALHO.size) { "" }
            val ocupadas = mutableSetOf<Int>()
            for (celula in linha.elementosFilhos("c")) {
                val ref = celula.getAttribute("r")
                val coluna = ref.firstOrNull()?.let { it - 'A' } ?: -1
                require(coluna in valores.indices && ref.drop(1).toIntOrNull() == indice + 1 && ocupadas.add(coluna))
                val valor = celula.elementosFilhos("v").singleOrNull()?.textContent ?: ""
                valores[coluna] = when (celula.getAttribute("t")) {
                    "s" -> textos.getOrNull(valor.toIntOrNull() ?: -1)
                        ?: throw IllegalArgumentException("Texto compartilhado inválido.")
                    "inlineStr" -> celula.elementosFilhos("is").singleOrNull()?.textContent ?: ""
                    "", "n" -> valor
                    else -> throw IllegalArgumentException("Tipo de célula não permitido.")
                }
            }
            valores
        }
    } catch (_: NoSuchElementException) {
        throw IllegalArgumentException("Componentes da planilha inválidos.")
    } catch (_: IOException) {
        throw IllegalArgumentException("Arquivo XLSX inválido.")
    } catch (_: SAXException) {
        throw IllegalArgumentException("XML de planilha inválido.")
    }
}

private fun lerPartes(bytes: ByteArray, maxExpandido: Int): Map<String, Document> {
    val documentos = mutableMapOf<String, Document>()
    val basicos = setOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels",
        "xl/styles.xml", "xl/sharedStrings.xml", "xl/theme/theme1.xml", "docProps/core.xml", "docProps/app.xml")
    val pastas = setOf("_rels/", "xl/", "xl/_rels/", "xl/theme/", "xl/worksheets/", "docProps/")
    val securos = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        isXIncludeAware = false
        isExpandEntityReferences = false
    }
    var tamanho = 0
    var partes = 0
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entrada = zip.nextEntry ?: break
            require(++partes <= 64)
            if (entrada.isDirectory) {
                require(entrada.name in pastas)
                require(zip.read() == -1) { "Diretório de planilha inválido." }
                continue
            }
            require(entrada.name in basicos || entrada.name.matches(Regex("xl/worksheets/sheet[1-9][0-9]*\\.xml"))) {
                "Componente de planilha não permitido."
            }
            require(entrada.name !in documentos)
            val xml = zip.readNBytes(maxExpandido - tamanho + 1)
            tamanho += xml.size
            if (tamanho > maxExpandido) throw ArquivoLoteExcessivo()
            val doc = securos.newDocumentBuilder().apply { setErrorHandler(DefaultHandler()) }
                .parse(ByteArrayInputStream(xml))
            if (entrada.name.endsWith(".rels")) {
                require(doc.elementos("Relationship").none {
                    it.getAttribute("TargetMode").equals("External", ignoreCase = true) ||
                        it.getAttribute("Target").contains(':')
                }) { "Vínculo externo não permitido." }
            }
            documentos[entrada.name] = doc
        }
    }
    return documentos
}

private fun Document.elementos(nome: String): List<Element> =
    getElementsByTagNameNS("*", nome).let { lista -> (0 until lista.length).map { lista.item(it) as Element } }

private fun Element.elementosFilhos(nome: String): List<Element> =
    childNodes.let { lista -> (0 until lista.length).mapNotNull { lista.item(it) as? Element } }
        .filter { it.localName == nome }
