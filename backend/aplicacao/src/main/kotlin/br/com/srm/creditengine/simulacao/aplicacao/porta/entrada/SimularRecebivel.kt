package br.com.srm.creditengine.simulacao.aplicacao.porta.entrada

fun interface SimularRecebivel {
    fun simular(comando: ComandoSimulacao): ResultadoSimulacao
}
