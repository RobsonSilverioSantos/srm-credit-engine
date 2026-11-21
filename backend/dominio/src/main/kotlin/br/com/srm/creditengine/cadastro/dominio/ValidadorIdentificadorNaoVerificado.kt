package br.com.srm.creditengine.cadastro.dominio

class ValidadorIdentificadorNaoVerificado : ValidadorDocumento {
    override fun normalizarEValidar(numero: String): String {
        require(numero.matches(Regex("[A-Z0-9]{1,32}"))) { "Identificador não verificado inválido" }
        return numero
    }
}
