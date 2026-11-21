package br.com.srm.creditengine.extrato.aplicacao

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao

class ServicoExtrato(private val contexto: ContextoOrganizacao, private val leitura: LeituraExtrato) : ConsultarExtrato {
    override fun consultar(filtro: FiltroExtrato): PaginaExtrato = leitura.consultar(contexto.organizacaoId(), filtro)
}
