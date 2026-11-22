package br.com.srm.creditengine.lotes.aplicacao

class ImportacaoInvalida(val linhas: List<PreviaLinhaLote>) : RuntimeException("Lote inválido.")
