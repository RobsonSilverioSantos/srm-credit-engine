package br.com.srm.creditengine.http.adaptador.entrada

data class CedenteEntrada(
    val nome: String,
    val documento: String,
    val pais: String? = null,
    val tipoDocumento: String? = null,
)
