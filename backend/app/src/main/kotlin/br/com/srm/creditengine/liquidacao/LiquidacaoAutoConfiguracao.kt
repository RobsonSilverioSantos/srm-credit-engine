package br.com.srm.creditengine.liquidacao

import br.com.srm.creditengine.cadastro.adaptador.saida.RecebiveisJdbc
import br.com.srm.creditengine.liquidacao.adaptador.saida.LiquidacaoJdbc
import br.com.srm.creditengine.liquidacao.aplicacao.ServicoLiquidacao
import br.com.srm.creditengine.liquidacao.aplicacao.porta.entrada.LiquidarRecebivel
import br.com.srm.creditengine.liquidacao.aplicacao.porta.saida.IdentidadeConfiavel
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import java.time.Clock
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

@AutoConfiguration
@AutoConfigureAfter(DataSourceTransactionManagerAutoConfiguration::class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(value = [JdbcTemplate::class, PlatformTransactionManager::class, TaxasPorta::class, IdentidadeConfiavel::class])
class LiquidacaoAutoConfiguracao {
    @Bean
    fun liquidarRecebivel(
        jdbc: JdbcTemplate,
        gerenciador: PlatformTransactionManager,
        taxas: TaxasPorta,
        identidade: IdentidadeConfiavel,
    ): LiquidarRecebivel {
        val clock = Clock.systemUTC()
        val persistencia = LiquidacaoJdbc(jdbc, TransactionTemplate(gerenciador), clock)
        val servico = ServicoLiquidacao(identidade, RecebiveisJdbc(jdbc), persistencia, taxas, persistencia, clock)
        return LiquidarRecebivel {
            check(!TransactionSynchronizationManager.isActualTransactionActive()) {
                "Liquidação deve iniciar fora de uma transação existente"
            }
            servico.liquidar(it)
        }
    }
}
