package br.com.srm.creditengine.taxas.aplicacao.porta.saida

import br.com.srm.creditengine.taxas.aplicacao.BoletimPtax
import br.com.srm.creditengine.taxas.aplicacao.PaginaCotacoes
import br.com.srm.creditengine.taxas.aplicacao.PaginaSelecoesCambiais
import br.com.srm.creditengine.taxas.aplicacao.SelecaoFonteCambial
import br.com.srm.creditengine.taxas.dominio.CotacaoAplicavel
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.FonteCambial
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface HistoricoCambio {
    fun registrarManual(autor: IdentidadeUsuario, taxa: BigDecimal, justificativa: String): CotacaoCambial
    fun selecionar(autor: IdentidadeUsuario, fonte: FonteCambial, cotacaoId: UUID?, justificativa: String): SelecaoFonteCambial
    fun consultar(organizacaoId: UUID, referencia: Instant): CotacaoAplicavel
    fun historico(organizacaoId: UUID, fonte: FonteCambial?, pagina: Int, tamanho: Int): PaginaCotacoes
    fun selecoes(organizacaoId: UUID, pagina: Int, tamanho: Int): PaginaSelecoesCambiais
    fun sincronizar(boletins: List<BoletimPtax>): Int
}
