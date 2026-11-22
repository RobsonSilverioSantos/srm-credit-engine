package br.com.srm.creditengine.http.adaptador.entrada

data class LoteRecebivelComandoHttp(
    val referenciaExterna: String,
    val cedenteId: String,
    val modalidade: String,
    val valorFaceBrl: String,
    val prazoMeses: Int,
    val vencimento: String?,
    val moedaPagamento: String,
)
