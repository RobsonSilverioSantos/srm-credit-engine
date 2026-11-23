package br.com.srm.creditengine.cadastro.dominio

import java.util.Locale

data class DocumentoEmpresarial(val pais: String, val tipoDocumento: String, val numero: String) {
    init {
        require(pais in PAISES_ISO) { "País ISO alpha-2 inválido" }
        val validador = when {
            pais == "BR" && tipoDocumento == "CNPJ" -> ValidadorCnpj()
            pais != "BR" && tipoDocumento == "OUTRO" -> ValidadorIdentificadorNaoVerificado()
            else -> throw IllegalArgumentException("Tipo não suportado; use OUTRO para identificador não verificado")
        }
        require(validador.normalizarEValidar(numero) == numero) { "Documento deve ser canônico" }
    }

    val status: StatusDocumento
        get() = if (pais == "BR" && tipoDocumento == "CNPJ") StatusDocumento.VALIDADO_DV else StatusDocumento.NAO_VERIFICADO

    companion object {
        private val PAISES_ISO = Locale.getISOCountries().toSet()

        fun validar(pais: String, tipoDocumento: String, numero: String): DocumentoEmpresarial {
            val canonico = if (pais == "BR" && tipoDocumento == "CNPJ") {
                ValidadorCnpj().normalizarEValidar(numero)
            } else {
                ValidadorIdentificadorNaoVerificado().normalizarEValidar(numero)
            }
            return DocumentoEmpresarial(pais, tipoDocumento, canonico)
        }
    }
}
