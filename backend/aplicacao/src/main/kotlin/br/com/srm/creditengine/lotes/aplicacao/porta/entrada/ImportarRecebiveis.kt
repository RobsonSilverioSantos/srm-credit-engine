package br.com.srm.creditengine.lotes.aplicacao.porta.entrada

import br.com.srm.creditengine.lotes.aplicacao.LinhaImportacao
import br.com.srm.creditengine.lotes.aplicacao.PreviaLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.ResultadoCadastroLote

interface ImportarRecebiveis {
    fun prevalidar(linhas: List<LinhaImportacao>): List<PreviaLinhaLote>
    fun confirmar(chave: String, linhas: List<LinhaImportacao>): ResultadoCadastroLote
}
