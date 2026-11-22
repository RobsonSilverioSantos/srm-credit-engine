package br.com.srm.creditengine.taxas.adaptador.saida

import br.com.srm.creditengine.taxas.aplicacao.porta.saida.CoordenarSincronizacaoPtax
import java.sql.SQLException
import javax.sql.DataSource
import org.springframework.transaction.support.TransactionSynchronizationManager

class CoordenacaoPtaxJdbc(private val dataSource: DataSource) : CoordenarSincronizacaoPtax {
    override fun executar(sincronizar: () -> Int): Int {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) { "Sincronização não aceita transação externa" }
        dataSource.connection.use { conexao ->
            check(conexao.autoCommit) { "Coordenação PTAX deve usar sessão sem transação aberta" }
            val adquiriu = conexao.createStatement().use { statement ->
                statement.executeQuery("SELECT pg_try_advisory_lock(20261122, 27)").use { rs -> rs.next(); rs.getBoolean(1) }
            }
            if (!adquiriu) throw ConsultaCambialIndisponivel(
                "Sincronização PTAX já em andamento", IllegalStateException("Capacidade PTAX ocupada"))
            try {
                // Lock de sessão entre instâncias: cobre HTTP e commit, sem transação durante a rede.
                return sincronizar()
            } finally {
                try {
                    conexao.createStatement().use { it.execute("SELECT pg_advisory_unlock(20261122, 27)") }
                } catch (e: SQLException) {
                    conexao.abort { it.run() }
                    throw e
                }
            }
        }
    }
}
