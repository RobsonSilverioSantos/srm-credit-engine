package br.com.srm.creditengine.usuarios.aplicacao

import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.ProvisionarMaster
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.CodificadorSenha
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.RepositorioUsuarios
import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import java.util.UUID

class ServicoProvisionarMaster(
    private val usuarios: RepositorioUsuarios,
    private val codificador: CodificadorSenha,
) : ProvisionarMaster {
    override fun executar(organizacaoId: UUID, email: String, senha: String): UsuarioOrganizacional {
        if (!usuarios.organizacaoAtiva(organizacaoId)) throw OrganizacaoNaoEncontrada()
        if (usuarios.existeMaster(organizacaoId)) throw MasterJaProvisionado()

        val usuario = UsuarioOrganizacional(
            id = UUID.randomUUID(),
            organizacaoId = organizacaoId,
            email = validarEmail(email),
            perfil = PerfilUsuario.MASTER,
            ativo = true,
        )
        validarSenha(senha)
        return usuarios.provisionarMaster(usuario, codificador.codificar(senha))
            ?: throw MasterJaProvisionado()
    }
}
