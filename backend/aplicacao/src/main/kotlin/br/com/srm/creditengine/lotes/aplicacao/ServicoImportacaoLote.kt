package br.com.srm.creditengine.lotes.aplicacao

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Cedentes
import br.com.srm.creditengine.cadastro.dominio.ValidadorCnpj
import br.com.srm.creditengine.lotes.aplicacao.porta.entrada.ImportarRecebiveis
import br.com.srm.creditengine.lotes.aplicacao.porta.saida.BuscarCedentePorCnpj
import br.com.srm.creditengine.lotes.aplicacao.porta.saida.PersistenciaLote
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.ComandoSimulacao
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.SimularRecebivel
import br.com.srm.creditengine.taxas.dominio.DadosFinanceirosIndisponiveis
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.ContextoUsuario
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID

class ServicoImportacaoLote(
    private val contexto: ContextoUsuario,
    private val cedentes: Cedentes,
    private val persistencia: PersistenciaLote,
    private val simulacoes: SimularRecebivel,
    private val maxLinhas: Int,
    private val buscarPorCnpj: BuscarCedentePorCnpj,
) : ImportarRecebiveis {
    override fun prevalidar(linhas: List<LinhaImportacao>): List<PreviaLinhaLote> {
        require(linhas.size in 1..maxLinhas) { "Quantidade de linhas inválida." }
        val id = contexto.identidade()
        return validar(id.organizacaoId, linhas.map { resolverDocumento(id.organizacaoId, it) }).map { previa ->
            if (previa.status != StatusLinhaLote.NOVO) previa else {
                val item = checkNotNull(previa.comando)
                try {
                    previa.copy(simulacao = simulacoes.simular(
                        ComandoSimulacao(item.modalidade, item.valorFaceBrl, item.prazoMeses, item.moedaPagamento),
                    ))
                } catch (_: DadosFinanceirosIndisponiveis) {
                    previa.copy(avisoSimulacao = "Dados financeiros indisponíveis para a simulação.")
                } catch (_: ArithmeticException) {
                    previa.copy(avisoSimulacao = "Não foi possível calcular a simulação para a linha.")
                } catch (_: IllegalArgumentException) {
                    previa.copy(avisoSimulacao = "Não foi possível calcular a simulação para a linha.")
                }
            }
        }
    }

    private fun resolverDocumento(org: UUID, linha: LinhaImportacao): LinhaImportacao {
        val informado = linha.documentoInformado ?: return linha
        val canonico = try {
            ValidadorCnpj().normalizarEValidar(informado)
        } catch (_: IllegalArgumentException) {
            return LinhaImportacao(linha.linha, null, linha.erros + ErroLinhaLote("cedenteDocumento", "Documento inválido."))
        }
        val cedenteId = buscarPorCnpj.buscar(org, canonico)
            ?: return LinhaImportacao(linha.linha, null,
                linha.erros + ErroLinhaLote("cedenteDocumento", "Cedente indisponível para a organização."))
        val documento = linha.documento ?: return LinhaImportacao(linha.linha, null, linha.erros)
        return LinhaImportacao(linha.linha, documento.comCedente(cedenteId), linha.erros)
    }

    override fun confirmar(chave: String, linhas: List<LinhaImportacao>): ResultadoCadastroLote {
        require(chave.isNotBlank() && chave.length <= 128 && chave.all { it.code in 33..126 }) { "Chave inválida." }
        require(linhas.size in 1..maxLinhas) { "Quantidade de linhas inválida." }
        if (linhas.any { it.item == null || it.erros.isNotEmpty() }) {
            throw ImportacaoInvalida(linhas.filter { it.item == null || it.erros.isNotEmpty() }.map {
                PreviaLinhaLote(it.linha, StatusLinhaLote.INVALIDO, null, it.erros)
            })
        }
        val id = contexto.identidade()
        val itens = linhas.map { checkNotNull(it.item) }
        return persistencia.confirmar(id, chave, hash(itens), itens) {
            val problemas = validar(id.organizacaoId, linhas).filter { it.status != StatusLinhaLote.NOVO }
            if (problemas.any { it.status == StatusLinhaLote.JA_CADASTRADO || it.status == StatusLinhaLote.JA_LIQUIDADO }) {
                throw ConflitoImportacaoLote()
            }
            if (problemas.isNotEmpty()) throw ImportacaoInvalida(problemas)
        }
    }

    private fun validar(org: UUID, linhas: List<LinhaImportacao>): List<PreviaLinhaLote> {
        require(linhas.size in 1..maxLinhas) { "Quantidade de linhas inválida." }
        val itens = linhas.mapNotNull { it.item }
        val contagens = itens.groupingBy { it.referenciaExterna }.eachCount()
        val existentes = persistencia.consultarReferencias(org, contagens.keys)
        val autorizados = itens.map { it.cedenteId }.distinct().associateWith { cedentes.buscar(org, it)?.ativo == true }
        return linhas.map { linha ->
            val item = linha.item
            if (item == null || linha.erros.isNotEmpty()) {
                PreviaLinhaLote(linha.linha, StatusLinhaLote.INVALIDO, null, linha.erros)
            } else if (contagens.getValue(item.referenciaExterna) > 1) {
                PreviaLinhaLote(linha.linha, StatusLinhaLote.INVALIDO, item,
                    listOf(ErroLinhaLote("referenciaExterna", "Referência repetida no lote.")))
            } else if (autorizados[item.cedenteId] != true) {
                PreviaLinhaLote(linha.linha, StatusLinhaLote.INVALIDO, item,
                    listOf(ErroLinhaLote("cedenteId", "Cedente indisponível para a organização.")))
            } else {
                val existente = existentes[item.referenciaExterna]
                PreviaLinhaLote(linha.linha, existente?.status ?: StatusLinhaLote.NOVO, item,
                    if (existente == null) emptyList() else
                        listOf(ErroLinhaLote("referenciaExterna", "Recebível já registrado nesta organização.")),
                    recebivelId = existente?.recebivelId)
            }
        }
    }

    private fun hash(itens: List<ItemImportacao>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for (item in itens) {
            // A referência tem alfabeto limitado, afastando colisões por separadores.
            val linha = listOf(item.referenciaExterna, item.cedenteId, item.modalidade,
                item.valorFaceBrl.setScale(2).toPlainString(), item.prazoMeses, item.vencimento ?: "",
                item.moedaPagamento.name).joinToString("|", postfix = "\n")
            digest.update(linha.toByteArray(Charsets.UTF_8))
        }
        return HexFormat.of().formatHex(digest.digest())
    }
}
