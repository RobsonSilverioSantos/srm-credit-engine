package br.com.srm.creditengine.taxas.dominio

import java.time.Instant
import java.util.UUID

data class CotacaoAplicavel(
    val cotacao: CotacaoCambial,
    val politicaId: UUID,
    val selecaoId: UUID?,
    val autorizacaoId: UUID?,
    val validaDesde: Instant,
    val validaAte: Instant,
    val limiteExclusivo: Boolean,
) {
    fun validar(referencia: Instant) {
        if (cotacao.vigenteEm > referencia || cotacao.criadoEm > referencia || validaDesde > referencia ||
            referencia > validaAte || (limiteExclusivo && referencia == validaAte)) {
            throw DadosFinanceirosIndisponiveis("Cotação da fonte selecionada indisponível no corte")
        }
    }
}
