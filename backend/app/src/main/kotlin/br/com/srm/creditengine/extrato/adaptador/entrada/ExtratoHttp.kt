package br.com.srm.creditengine.extrato.adaptador.entrada

import br.com.srm.creditengine.extrato.aplicacao.ConsultarExtrato
import br.com.srm.creditengine.extrato.aplicacao.FiltroExtrato
import br.com.srm.creditengine.extrato.aplicacao.ItemExtrato
import br.com.srm.creditengine.liquidacao.dominio.MoedaPagamento
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.time.LocalDate
import java.util.UUID
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@RequestMapping(value = ["/api/v1", "/bff/v1"])
@SecurityRequirement(name = "bearerAuth")
class ExtratoHttp(private val extrato: ObjectProvider<ConsultarExtrato>) {
    private fun ItemExtrato.resposta() = ItemExtratoResposta(
        liquidacaoId, recebivelId, cedenteId, cedenteNome, modalidade, valorFaceBrl.toPlainString(), prazoMeses, versaoRecebivel,
        taxaBaseId, taxaBaseMensal.toPlainString(), taxaBaseVigenteEm.toString(), spreadId, spreadMensal.toPlainString(),
        spreadVigenteEm.toString(), valorPresenteBrl.toPlainString(), desagioBrl.toPlainString(), moeda.name,
        cotacaoCambialId, taxaBrlPorUnidade?.toPlainString(), cotacaoVigenteEm?.toString(),
        cotacaoValidaAte?.toString(), valorPagamento.toPlainString(), liquidadaEm.toString(),
    )

    @GetMapping("/extrato")
    @Operation(summary = "Consultar extrato de liquidações",
        description = "Datas inclusivas em UTC; ordenação por liquidação e ID decrescentes.")
    fun consultar(
        @Parameter(description = "Data inicial inclusiva em UTC (AAAA-MM-DD)")
        @RequestParam(required = false) inicio: String?,
        @Parameter(description = "Data final inclusiva em UTC (AAAA-MM-DD)")
        @RequestParam(required = false) fim: String?,
        @RequestParam(required = false) cedenteId: String?,
        @RequestParam(required = false) moeda: String?,
        @Parameter(description = "Página baseada em zero; padrão 0")
        @RequestParam(required = false) pagina: String?,
        @Parameter(description = "Itens por página, de 1 a 100; padrão 20")
        @RequestParam(required = false) tamanho: String?,
    ): PaginaExtratoResposta {
        fun data(valor: String?): LocalDate? = valor?.let {
            require(it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "Data inválida" }
            LocalDate.parse(it).also { d -> require(d.year in 1..9999) { "Data inválida" } }
        }
        fun inteiro(valor: String?, padrao: Int): Int = valor?.let {
            require(it.matches(Regex("[0-9]+"))) { "Paginação inválida" }
            it.toInt()
        } ?: padrao
        val cedente = cedenteId?.let {
            require(it.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
                "UUID inválido"
            }
            UUID.fromString(it)
        }
        val filtro = FiltroExtrato(data(inicio), data(fim), cedente, moeda?.let(MoedaPagamento::valueOf),
            inteiro(pagina, 0), inteiro(tamanho, 20))
        val resultado = extrato.`object`.consultar(filtro)
        return PaginaExtratoResposta(resultado.itens.map { it.resposta() }, resultado.pagina, resultado.tamanho, resultado.total)
    }
}
