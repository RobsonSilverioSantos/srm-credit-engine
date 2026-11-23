package br.com.srm.creditengine.cadastro.aplicacao

import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.Cadastro
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoCedente
import br.com.srm.creditengine.cadastro.aplicacao.porta.entrada.NovoRecebivel
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Cedentes
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.ContextoOrganizacao
import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Recebiveis
import br.com.srm.creditengine.cadastro.dominio.Cedente
import br.com.srm.creditengine.cadastro.dominio.DocumentoEmpresarial
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import java.util.UUID

class ServicoCadastro(
    private val contexto: ContextoOrganizacao,
    private val cedentes: Cedentes,
    private val recebiveis: Recebiveis,
) : Cadastro {
    override fun cadastrarCedente(comando: NovoCedente): Cedente {
        val organizacaoId = contexto.organizacaoId()
        require((comando.pais == null) == (comando.tipoDocumento == null)) { "País e tipo devem ser informados juntos" }
        val documento = DocumentoEmpresarial.validar(
            comando.pais ?: "BR", comando.tipoDocumento ?: "CNPJ", comando.documento,
        )
        return cedentes.cadastrar(Cedente(
            UUID.randomUUID(), organizacaoId, comando.nome, documento.numero, identificacao = documento,
        ))
    }

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
