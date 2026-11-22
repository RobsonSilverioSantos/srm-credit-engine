package br.com.srm.creditengine.http.adaptador.entrada

import br.com.srm.creditengine.lotes.aplicacao.ErroLinhaLote
import br.com.srm.creditengine.lotes.aplicacao.StatusLinhaLote
import java.util.UUID

data class LoteRecebivelLinhaHttp(
    val linha: Int,
    val status: StatusLinhaLote,
    val comando: LoteRecebivelComandoHttp?,
    val erros: List<ErroLinhaLote>,
    val simulacao: SimulacaoResposta?,
    val avisoSimulacao: String?,
    val recebivelId: UUID?,
)
