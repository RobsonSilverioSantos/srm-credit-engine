package br.com.srm.creditengine.taxas.aplicacao

import br.com.srm.creditengine.taxas.dominio.FonteCambial
import java.time.Instant
import java.util.UUID

data class SelecaoFonteCambial(
    val id: UUID,
    val fonte: FonteCambial,
    val cotacaoId: UUID?,
    val autorizadoEm: Instant,
    val validaAte: Instant?,
    val politicaId: UUID,
    val autorId: UUID? = null,
    val justificativa: String? = null,
)
