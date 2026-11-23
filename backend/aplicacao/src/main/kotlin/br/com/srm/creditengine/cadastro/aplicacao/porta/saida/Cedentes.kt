package br.com.srm.creditengine.cadastro.aplicacao.porta.saida

import br.com.srm.creditengine.cadastro.dominio.Cedente
import java.util.UUID

interface Cedentes {
    fun cadastrar(cedente: Cedente): Cedente
    fun buscar(organizacaoId: UUID, id: UUID): Cedente?
    fun listar(organizacaoId: UUID): List<Cedente>
}
