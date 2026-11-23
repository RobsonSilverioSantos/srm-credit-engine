package br.com.srm.creditengine.usuarios.aplicacao.porta.entrada

import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import java.util.UUID

fun interface ProvisionarMaster {
    fun executar(organizacaoId: UUID, email: String, senha: String): UsuarioOrganizacional
}
