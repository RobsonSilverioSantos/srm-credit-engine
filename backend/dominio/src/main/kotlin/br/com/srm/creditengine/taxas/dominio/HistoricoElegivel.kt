package br.com.srm.creditengine.taxas.dominio

data class HistoricoElegivel(
    val taxaBase: TaxaBase?,
    val spread: SpreadRecebivel?,
    val cotacao: CotacaoCambial?,
)
