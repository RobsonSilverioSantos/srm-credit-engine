package br.com.srm.creditengine.lotes.aplicacao

data class LinhaImportacao(
    val linha: Int,
    val item: ItemImportacao?,
    val erros: List<ErroLinhaLote>,
    val documento: ItemImportacaoDocumento? = null,
    val documentoInformado: String? = null,
)
