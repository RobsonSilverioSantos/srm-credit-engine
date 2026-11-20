package br.com.srm.creditengine.cadastro.aplicacao.porta.entrada

import br.com.srm.creditengine.cadastro.dominio.Cedente
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class NovoCedente(val nome: String, val documento: String)

data class NovoRecebivel(
    val cedenteId: UUID,
    val modalidade: String,
    val valorFaceBrl: BigDecimal,
    val prazoMeses: Int,
    val vencimento: LocalDate? = null,
)

interface Cadastro {
    fun cadastrarCedente(comando: NovoCedente): Cedente
    fun buscarCedente(id: UUID): Cedente?
    fun listarCedentes(): List<Cedente>
    fun cadastrarRecebivel(comando: NovoRecebivel): Recebivel
    fun buscarRecebivel(id: UUID): Recebivel?
    fun listarRecebiveis(): List<Recebivel>
}
