package br.com.srm.creditengine.taxas.dominio

import java.time.Instant

data class TaxasParaSnapshot(
    val referencia: Instant,
    val taxaBase: TaxaBase,
    val spread: SpreadRecebivel,
    val cotacao: CotacaoCambial?,
    val cotacaoValidaAte: Instant?,
)
