package br.com.srm.creditengine.cadastro.aplicacao

import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoCedente
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoRecebivel
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Cedentes
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Recebiveis
import br.com.srm.creditengine.cadastro.dominio.Cedente
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import java.util.UUID

class ServicoCadastro(
    private val contexto: ContextoOrganizacao,
    private val cedentes: Cedentes,
    private val recebiveis: Recebiveis,
) : Cadastro {
    override fun cadastrarCedente(comando: NovoCedente): Cedente =
        cedentes.cadastrar(Cedente(UUID.randomUUID(), contexto.organizacaoId(), comando.nome, comando.documento))

    override fun buscarCedente(id: UUID): Cedente? = cedentes.buscar(contexto.organizacaoId(), id)

    override fun listarCedentes(): List<Cedente> = cedentes.listar(contexto.organizacaoId())

    override fun cadastrarRecebivel(comando: NovoRecebivel): Recebivel {
        val organizacaoId = contexto.organizacaoId()
        val recebivel = Recebivel(
            UUID.randomUUID(), organizacaoId, comando.cedenteId, comando.modalidade,
            comando.valorFaceBrl, comando.prazoMeses, comando.vencimento,
        )
        if (cedentes.buscar(organizacaoId, comando.cedenteId) == null) throw CedenteNaoEncontrado()
        return recebiveis.cadastrar(recebivel.copy(valorFaceBrl = recebivel.valorFaceBrl.setScale(2)))
    }

    override fun buscarRecebivel(id: UUID): Recebivel? = recebiveis.buscar(contexto.organizacaoId(), id)

    override fun listarRecebiveis(): List<Recebivel> = recebiveis.listar(contexto.organizacaoId())
}
