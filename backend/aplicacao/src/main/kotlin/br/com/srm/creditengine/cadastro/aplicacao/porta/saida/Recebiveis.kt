package br.com.srm.creditengine.cadastro.aplicacao.porta.saida

import br.com.srm.creditengine.cadastro.dominio.Recebivel
import java.util.UUID

interface Recebiveis {
    fun cadastrar(recebivel: Recebivel): Recebivel
    fun buscar(organizacaoId: UUID, id: UUID): Recebivel?
    fun listar(organizacaoId: UUID): List<Recebivel>
}
