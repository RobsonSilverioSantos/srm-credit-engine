import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    base
    kotlin("jvm") version "2.2.21" apply false
    kotlin("plugin.spring") version "2.2.21" apply false
    id("org.springframework.boot") version "4.0.10" apply false
}

subprojects {
    repositories {
        mavenCentral()
    }

    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }
}

val classpathCompiladorKotlin = files(provider {
    project(":dominio").configurations.getByName("kotlinCompilerClasspath")
})
val compilarVerificadorKotlin = tasks.register<JavaCompile>("compilarVerificadorTiposKotlin") {
    setSource(files("scripts/VerificarTiposKotlin.java"))
    classpath = classpathCompiladorKotlin
    destinationDirectory = layout.buildDirectory.dir("verificador-tipos-kotlin")
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.release = 21
}
val verificarTiposKotlin = tasks.register<JavaExec>("verificarTiposKotlin") {
    group = "verification"
    description = "Verifica tipos top-level Kotlin de produção com o parser do compilador."
    dependsOn(compilarVerificadorKotlin)
    classpath = files(compilarVerificadorKotlin.flatMap { it.destinationDirectory }, classpathCompiladorKotlin)
    mainClass = "VerificarTiposKotlin"
    val fontes = listOf("dominio", "aplicacao", "app").map {
        layout.projectDirectory.dir("$it/src/main/kotlin")
    }
    inputs.files(fontes.map { fileTree(it) { include("**/*.kt") } })
    args(fontes.map { it.asFile.absolutePath })
}
tasks.named("check") { dependsOn(verificarTiposKotlin) }
subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        tasks.named("check") { dependsOn(verificarTiposKotlin) }
    }
}

mapOf(":dominio" to emptySet(), ":aplicacao" to setOf(":dominio")).forEach { (camada, permitidos) ->
    val modulo = project(camada)
    modulo.plugins.withId("org.jetbrains.kotlin.jvm") {
        val classpaths = listOf("compileClasspath", "runtimeClasspath").map { nome ->
            modulo.configurations.named(nome)
        }
        val verificarFronteira = modulo.tasks.register("verificarFronteiraHexagonal") {
            group = "verification"
            description = "Verifica se o núcleo não depende de frameworks ou adaptadores."

            doLast {
                classpaths.forEach { classpath ->
                    val configuracao = classpath.get()
                    val dependencias = configuracao.incoming.artifacts.artifacts.map { it.id.componentIdentifier }
                    val indevidos = dependencias.distinct().filterNot { id ->
                        when (id) {
                            is ModuleComponentIdentifier ->
                                (id.group == "org.jetbrains.kotlin" &&
                                    id.module == "kotlin-stdlib") ||
                                    (id.group == "org.jetbrains" && id.module == "annotations")
                            is ProjectComponentIdentifier -> id.projectPath == camada || id.projectPath in permitidos
                            else -> false
                        }
                    }
                    check(indevidos.isEmpty()) {
                        "$camada:${configuracao.name} contém dependências fora da fronteira: $indevidos"
                    }
                }
            }
        }
        modulo.tasks.named("check") { dependsOn(verificarFronteira) }
    }
}
