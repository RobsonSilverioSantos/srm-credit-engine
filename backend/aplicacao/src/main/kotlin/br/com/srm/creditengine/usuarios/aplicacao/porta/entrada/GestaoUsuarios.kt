package br.com.srm.creditengine.usuarios.aplicacao.porta.entrada

import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import java.util.UUID

interface GestaoUsuarios {
    fun minhaConta(): MinhaConta
    fun listar(): List<UsuarioOrganizacional>
    fun criar(comando: NovoUsuarioOrganizacional): UsuarioOrganizacional
    fun alterarStatus(usuarioId: UUID, ativo: Boolean): UsuarioOrganizacional
}
