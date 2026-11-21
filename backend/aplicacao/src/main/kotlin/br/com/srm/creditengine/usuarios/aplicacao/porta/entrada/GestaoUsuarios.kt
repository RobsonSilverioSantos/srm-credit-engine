package br.com.srm.creditengine.usuarios.aplicacao.porta.entrada

import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import java.util.UUID

data class NovoUsuarioOrganizacional(
    val email: String,
    val senha: String,
    val perfil: PerfilUsuario,
)

data class MinhaConta(
    val usuario: UsuarioOrganizacional,
    val perfisCriaveis: List<PerfilUsuario>,
    val podeListarUsuarios: Boolean,
    val podeAlterarStatus: Boolean,
)

interface GestaoUsuarios {
    fun minhaConta(): MinhaConta
    fun listar(): List<UsuarioOrganizacional>
    fun criar(comando: NovoUsuarioOrganizacional): UsuarioOrganizacional
    fun alterarStatus(usuarioId: UUID, ativo: Boolean): UsuarioOrganizacional
}

fun interface ProvisionarMaster {
    fun executar(organizacaoId: UUID, email: String, senha: String): UsuarioOrganizacional
}
