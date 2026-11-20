package br.com.srm.creditengine

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class CreditEngineApplication

fun main(args: Array<String>) {
    runApplication<CreditEngineApplication>(*args)
}
