package br.com.srm.creditengine.cadastro.dominio

import java.util.UUID

data class Cedente(
    val id: UUID,
    val organizacaoId: UUID,
    val nome: String,
    val documento: String,
    val ativo: Boolean = true,
    val identificacao: DocumentoEmpresarial? = null,
) {
    init {
        require(nome.isNotBlank() && nome.length <= 160) { "Nome do cedente inválido" }
        require(documento.isNotBlank() && documento.length <= 32) { "Documento do cedente inválido" }
        require(identificacao == null || identificacao.numero == documento) { "Identificação inconsistente" }
    }
}
