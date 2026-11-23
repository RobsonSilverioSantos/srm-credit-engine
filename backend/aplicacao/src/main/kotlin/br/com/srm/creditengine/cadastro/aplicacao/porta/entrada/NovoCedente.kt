package br.com.srm.creditengine.cadastro.aplicacao.porta.entrada

data class NovoCedente(
    val nome: String,
    val documento: String,
    val pais: String? = null,
    val tipoDocumento: String? = null,
)
