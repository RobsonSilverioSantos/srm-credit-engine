package br.com.srm.creditengine.lotes.aplicacao.porta.saida

import br.com.srm.creditengine.lotes.aplicacao.ItemImportacao
import br.com.srm.creditengine.lotes.aplicacao.ResultadoCadastroLote
import br.com.srm.creditengine.lotes.aplicacao.ReferenciaExistenteLote
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import java.util.UUID

interface PersistenciaLote {
    fun consultarReferencias(organizacaoId: UUID, referencias: Set<String>): Map<String, ReferenciaExistenteLote>

    // A verificação da intenção precede a revalidação, para que replay não seja confundido com duplicidade.
    fun confirmar(
        identidade: IdentidadeUsuario,
        chave: String,
        hash: String,
        itens: List<ItemImportacao>,
        revalidar: () -> Unit,
    ): ResultadoCadastroLote
}
