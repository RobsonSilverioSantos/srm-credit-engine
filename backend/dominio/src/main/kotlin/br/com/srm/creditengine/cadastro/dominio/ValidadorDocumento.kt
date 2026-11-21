package br.com.srm.creditengine.cadastro.dominio

interface ValidadorDocumento {
    fun normalizarEValidar(numero: String): String
}
