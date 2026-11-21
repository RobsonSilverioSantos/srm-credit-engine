package br.com.srm.creditengine.usuarios

internal interface TerminalProvisionamentoMaster {
    fun lerTexto(pergunta: String): String?
    fun lerSenha(pergunta: String): CharArray?
    fun informar(mensagem: String)
}
