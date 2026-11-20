package br.com.srm.creditengine.cadastro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class BootstrapConfiguracaoTest {
    @Test
    fun `bootstrap exige perfil e propriedade explicitos`() {
        listOf(false to false, false to true, true to false).forEach { (demo, habilitado) ->
            AnnotationConfigApplicationContext().use { context ->
                if (demo) context.environment.setActiveProfiles("demo")
                context.environment.propertySources.addFirst(
                    MapPropertySource("teste", mapOf("srm.demo.bootstrap-organizacao" to habilitado.toString())),
                )
                context.register(CadastroConfiguracao::class.java)
                context.refresh()
                assertThat(context.containsBean("bootstrapOrganizacao")).isFalse()
            }
        }
    }
}
