package br.com.srm.creditengine.cadastro.aplicacao.porta.saida

import br.com.srm.creditengine.cadastro.dominio.Cedente
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import java.util.UUID

fun interface ContextoOrganizacao {
    fun organizacaoId(): UUID
}

interface Cedentes {
    fun cadastrar(cedente: Cedente): Cedente
    fun buscar(organizacaoId: UUID, id: UUID): Cedente?
    fun listar(organizacaoId: UUID): List<Cedente>
}

interface Recebiveis {
    fun cadastrar(recebivel: Recebivel): Recebivel
    fun buscar(organizacaoId: UUID, id: UUID): Recebivel?
    fun listar(organizacaoId: UUID): List<Recebivel>
}
