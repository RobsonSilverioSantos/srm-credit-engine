package br.com.srm.creditengine.lotes.aplicacao

import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.ResultadoSimulacao
import java.util.UUID

data class PreviaLinhaLote(
    val linha: Int,
    val status: StatusLinhaLote,
    val comando: ItemImportacao?,
    val erros: List<ErroLinhaLote>,
    val simulacao: ResultadoSimulacao? = null,
    val avisoSimulacao: String? = null,
    val recebivelId: UUID? = null,
)
