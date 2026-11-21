package br.com.srm.creditengine.taxas

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.core.env.MapPropertySource

class BootstrapTaxasDemoCondicaoTest {
    @Test
    fun `bootstrap financeiro exige perfil demo e habilitacao explicita`() {
        listOf(false to false, false to true, true to false).forEach { (demo, habilitado) ->
            AnnotationConfigApplicationContext().use { context ->
                if (demo) context.environment.setActiveProfiles("demo")
                context.environment.propertySources.addFirst(
                    MapPropertySource("teste", mapOf("srm.demo.bootstrap-taxas" to habilitado.toString())),
                )
                context.register(BootstrapTaxasDemo::class.java)
                context.refresh()
                assertThat(context.containsBean("bootstrapTaxas")).isFalse()
            }
        }
    }
}
