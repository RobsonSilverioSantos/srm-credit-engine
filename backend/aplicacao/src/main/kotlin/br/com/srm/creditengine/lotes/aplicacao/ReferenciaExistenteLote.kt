package br.com.srm.creditengine.lotes.aplicacao

import java.util.UUID

data class ReferenciaExistenteLote(val recebivelId: UUID, val status: StatusLinhaLote)
