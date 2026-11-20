package br.com.srm.creditengine.simulacao

import br.com.srm.creditengine.simulacao.aplicacao.ServicoSimulacao
import br.com.srm.creditengine.simulacao.aplicacao.porta.entrada.SimularRecebivel
import br.com.srm.creditengine.taxas.aplicacao.porta.entrada.TaxasPorta
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = ["srm.taxas.enabled"], havingValue = "true", matchIfMissing = true)
class ConfiguracaoSimulacao {
    @Bean
    fun simularRecebivel(taxas: TaxasPorta): SimularRecebivel = ServicoSimulacao(taxas)
}
