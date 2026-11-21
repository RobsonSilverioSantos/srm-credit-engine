package br.com.srm.creditengine.http.adaptador.entrada

import java.util.UUID

data class CedenteResposta(val id: UUID, val nome: String, val documento: String, val ativo: Boolean)
