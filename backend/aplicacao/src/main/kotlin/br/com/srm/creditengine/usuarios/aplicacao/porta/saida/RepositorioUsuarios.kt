package br.com.srm.creditengine.usuarios.aplicacao.porta.saida

import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import java.util.UUID

interface RepositorioUsuarios {
    fun buscar(organizacaoId: UUID, usuarioId: UUID): UsuarioOrganizacional?
    fun listar(organizacaoId: UUID, perfis: Set<PerfilUsuario>): List<UsuarioOrganizacional>
    fun inserir(usuario: UsuarioOrganizacional, senhaHash: String): UsuarioOrganizacional
    fun provisionarMaster(usuario: UsuarioOrganizacional, senhaHash: String): UsuarioOrganizacional?
    fun organizacaoAtiva(organizacaoId: UUID): Boolean
    fun existeMaster(organizacaoId: UUID): Boolean
    fun alterarStatus(
        organizacaoId: UUID,
        usuarioId: UUID,
        perfil: PerfilUsuario,
        ativo: Boolean,
    ): Boolean
}
