package br.com.srm.creditengine.http.adaptador.entrada

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = Info(title = "SRM Credit Engine", version = "v1",
    description = "Cadastro precede liquidação. Simulação pré-cadastro e extrato ainda não estão disponíveis. " +
        "A manutenção global de taxas e câmbio não é exposta por HTTP."))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
class OpenApiConfiguracao
