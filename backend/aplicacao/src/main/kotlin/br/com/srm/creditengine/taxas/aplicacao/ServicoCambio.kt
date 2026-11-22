package br.com.srm.creditengine.taxas.aplicacao

import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.ConsultarCambio
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.GestaoCambio
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.ConsultarPtax
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.CoordenarSincronizacaoPtax
import br.com.srm.creditengine.taxas.aplicacao.porta.saida.HistoricoCambio
import br.com.srm.creditengine.taxas.dominio.CotacaoCambial
import br.com.srm.creditengine.taxas.dominio.FonteCambial
import br.com.srm.creditengine.usuarios.aplicacao.AcessoAdministrativoNegado
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.ContextoUsuario
import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.RepositorioUsuarios
import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ServicoCambio(
    private val contexto: ContextoUsuario,
    private val historico: HistoricoCambio,
    private val ptax: ConsultarPtax,
    private val relogio: Clock,
    private val usuarios: RepositorioUsuarios,
    private val coordenacao: CoordenarSincronizacaoPtax,
) : GestaoCambio, ConsultarCambio {
    private fun perfil() = contexto.identidade().let {
        usuarios.buscar(it.organizacaoId, it.usuarioId)?.takeIf { usuario -> usuario.ativo }?.perfil
            ?: throw AcessoAdministrativoNegado()
    }

    private fun administrador() = contexto.identidade().also {
        if (perfil() !in setOf(PerfilUsuario.MASTER, PerfilUsuario.ADMIN)) throw AcessoAdministrativoNegado()
    }

    private fun motivo(valor: String): String {
        require(valor.trim().length in 1..1000 && valor.none { it.isISOControl() }) { "Justificativa inválida" }
        return valor.trim()
    }

    override fun registrarManual(taxa: BigDecimal, justificativa: String): CotacaoCambial {
        val autor = administrador()
        CotacaoCambial(UUID.randomUUID(), taxa, Instant.EPOCH, Instant.EPOCH)
        return historico.registrarManual(autor, taxa, motivo(justificativa))
    }

    override fun selecionar(fonte: FonteCambial, cotacaoId: UUID?, justificativa: String): SelecaoFonteCambial {
        val autor = administrador()
        require(fonte == FonteCambial.BCB_PTAX || fonte == FonteCambial.MANUAL) { "Fonte não selecionável" }
        require((fonte == FonteCambial.MANUAL) == (cotacaoId != null)) { "Cotação manual obrigatória somente para MANUAL" }
        return historico.selecionar(autor, fonte, cotacaoId, motivo(justificativa))
    }

    override fun consultar(referencia: Instant) = historico.consultar(contexto.identidade().organizacaoId, referencia)

    override fun historico(fonte: FonteCambial?, pagina: Int, tamanho: Int): PaginaCotacoes {
        require(pagina in 0..1_000_000 && tamanho in 1..100) { "Paginação inválida" }
        val usuario = contexto.identidade()
        val resultado = historico.historico(usuario.organizacaoId, fonte, pagina, tamanho)
        return if (perfil() == PerfilUsuario.OPERADOR) resultado.copy(itens = resultado.itens.map {
            it.copy(autorId = null, justificativa = null)
        }) else resultado
    }

    override fun selecoes(pagina: Int, tamanho: Int): PaginaSelecoesCambiais {
        require(pagina in 0..1_000_000 && tamanho in 1..100) { "Paginação inválida" }
        val resultado = historico.selecoes(contexto.identidade().organizacaoId, pagina, tamanho)
        return if (perfil() == PerfilUsuario.OPERADOR) resultado.copy(itens = resultado.itens.map {
            it.copy(autorId = null, justificativa = null)
        }) else resultado
    }

    override fun sincronizar(): Int {
        administrador()
        val fim = relogio.instant().atZone(ZoneId.of("America/Sao_Paulo")).toLocalDate()
        // Consulta limitada a sete dias; idade financeira continua limitada pela política versionada.
        return coordenacao.executar { historico.sincronizar(ptax.consultar(fim.minusDays(7), fim)) }
    }
}
