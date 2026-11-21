package br.com.srm.creditengine.usuarios.aplicacao

import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.GestaoUsuarios
import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.MinhaConta
import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.NovoUsuarioOrganizacional
import br.com.srm.creditengine.usuarios.aplicacao.porta.entrada.ProvisionarMaster
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.CodificadorSenha
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.ContextoUsuario
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.RepositorioUsuarios
import br.com.srm.creditengine.usuarios.dominio.IdentidadeUsuario
import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

class AcessoAdministrativoNegado : RuntimeException()
class UsuarioAdministrativoNaoEncontrado : RuntimeException()
class OrganizacaoNaoEncontrada : RuntimeException()
class MasterJaProvisionado : RuntimeException()

class ServicoGestaoUsuarios(
    private val contexto: ContextoUsuario,
    private val usuarios: RepositorioUsuarios,
    private val codificador: CodificadorSenha,
) : GestaoUsuarios {
    override fun minhaConta(): MinhaConta {
        val identidade = contexto.identidade()
        val usuario = usuarioAtivo(identidade)
        val perfisGerenciaveis = usuario.perfil.perfisGerenciaveis()
        return MinhaConta(
            usuario = usuario,
            perfisCriaveis = usuario.perfil.perfisCriaveis().toList(),
            podeListarUsuarios = perfisGerenciaveis.isNotEmpty(),
            podeAlterarStatus = perfisGerenciaveis.isNotEmpty(),
        )
    }

    override fun listar(): List<UsuarioOrganizacional> {
        val identidade = contexto.identidade()
        val ator = usuarioAtivo(identidade)
        val perfisGerenciaveis = ator.perfil.perfisGerenciaveis()
        if (perfisGerenciaveis.isEmpty()) throw AcessoAdministrativoNegado()
        return usuarios.listar(identidade.organizacaoId, perfisGerenciaveis)
    }

    override fun criar(comando: NovoUsuarioOrganizacional): UsuarioOrganizacional {
        val identidade = contexto.identidade()
        val ator = usuarioAtivo(identidade)
        if (comando.perfil !in ator.perfil.perfisCriaveis()) throw AcessoAdministrativoNegado()

        val email = validarEmail(comando.email)
        validarSenha(comando.senha)
        return usuarios.inserir(
            UsuarioOrganizacional(
                id = UUID.randomUUID(),
                organizacaoId = identidade.organizacaoId,
                email = email,
                perfil = comando.perfil,
                ativo = true,
            ),
            codificador.codificar(comando.senha),
        )
    }

    override fun alterarStatus(usuarioId: UUID, ativo: Boolean): UsuarioOrganizacional {
        val identidade = contexto.identidade()
        val ator = usuarioAtivo(identidade)
        val perfisGerenciaveis = ator.perfil.perfisGerenciaveis()
        if (perfisGerenciaveis.isEmpty()) throw AcessoAdministrativoNegado()

        val alvo = usuarios.buscar(identidade.organizacaoId, usuarioId)
            ?: throw UsuarioAdministrativoNaoEncontrado()
        if (alvo.id == ator.id || alvo.perfil !in perfisGerenciaveis) {
            throw AcessoAdministrativoNegado()
        }
        if (!usuarios.alterarStatus(identidade.organizacaoId, alvo.id, alvo.perfil, ativo)) {
            throw UsuarioAdministrativoNaoEncontrado()
        }
        return alvo.copy(ativo = ativo)
    }

    private fun usuarioAtivo(identidade: IdentidadeUsuario): UsuarioOrganizacional =
        usuarios.buscar(identidade.organizacaoId, identidade.usuarioId)
            ?.takeIf { it.ativo }
            ?: throw AcessoAdministrativoNegado()
}

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

private fun validarEmail(valor: String): String {
    val email = valor.trim().lowercase(Locale.ROOT)
    require(email.length <= 254 && email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
        "E-mail inválido"
    }
    return email
}

private fun validarSenha(senha: String) {
    val tamanhoEmBytes = senha.toByteArray(StandardCharsets.UTF_8).size
    require(tamanhoEmBytes in 12..72) { "A senha deve ter entre 12 e 72 bytes UTF-8" }
}
