package br.com.srm.creditengine.lotes.adaptador.entrada

import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import br.com.srm.creditengine.lotes.aplicacao.ErroLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.ItemImportacao
import br.com.srm.creditengine.lotes.aplicacao.ItemImportacaoDocumento
import br.com.srm.creditengine.lotes.aplicacao.LinhaImportacao
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

internal fun interpretarLinhaLote(linha: Int, valores: List<String>, porDocumento: Boolean = false): LinhaImportacao {
    if (valores.size != LeitorArquivoLote.CABECALHO.size) {
        return LinhaImportacao(linha, null, listOf(ErroLinhaLote("linha", "Quantidade de colunas inválida.")))
    }
    val cabecalho = if (porDocumento) LeitorArquivoLote.CABECALHO_DOCUMENTO else LeitorArquivoLote.CABECALHO
    val dados = cabecalho.zip(valores).toMap()
    val erros = mutableListOf<ErroLinhaLote>()
    fun campo(nome: String, valido: Boolean) {
        if (!valido) erros.add(ErroLinhaLote(nome, "Valor inválido."))
    }
    val ref = dados.getValue("referenciaExterna")
    campo("referenciaExterna", ref.matches(Regex("[A-Za-z0-9._:/-]{1,128}")))
    val cedente = dados["cedenteId"]?.takeIf {
        it.matches(Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))
    }?.let(UUID::fromString)
    if (!porDocumento) campo("cedenteId", cedente != null)
    val modalidade = dados.getValue("modalidade")
    campo("modalidade", modalidade == "DUPLICATA_MERCANTIL" || modalidade == "CHEQUE_PRE_DATADO")
    val valor = dados.getValue("valorFaceBrl").takeIf { it.matches(Regex("[0-9]{1,17}(\\.[0-9]{1,2})?")) }
        ?.let(::BigDecimal)?.takeIf { it.signum() > 0 }
    campo("valorFaceBrl", valor != null)
    val prazo = dados.getValue("prazoMeses").toIntOrNull()?.takeIf { it > 0 }
    campo("prazoMeses", prazo != null)
    val venc = dados.getValue("vencimento")
    val vencimento = try {
        venc.takeIf { it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) }?.let(LocalDate::parse)?.takeIf { it.year > 0 }
    } catch (_: DateTimeParseException) {
        null
    }
    campo("vencimento", venc.isEmpty() || vencimento != null)
    val moeda = MoedaPagamento.entries.firstOrNull { it.name == dados.getValue("moedaPagamento") }
    campo("moedaPagamento", moeda != null)
    val item = if (erros.isEmpty() && !porDocumento) ItemImportacao(ref, checkNotNull(cedente), modalidade, checkNotNull(valor).setScale(2),
        checkNotNull(prazo), vencimento, checkNotNull(moeda)) else null
    val documento = if (erros.isEmpty() && porDocumento) ItemImportacaoDocumento(
        ref, modalidade, checkNotNull(valor).setScale(2),
        checkNotNull(prazo), vencimento, checkNotNull(moeda),
    ) else null
    return LinhaImportacao(linha, item, erros, documento, dados["cedenteDocumento"])
}
