package br.com.srm.creditengine.http.adaptador.entrada

import java.util.UUID

data class CedenteResposta(
    val id: UUID,
    val nome: String,
    val documento: String,
    val ativo: Boolean,
    val pais: String?,
    val tipoDocumento: String?,
    val documentoCanonico: String?,
    val statusDocumento: String,
)
