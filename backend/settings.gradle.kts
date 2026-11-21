pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "srm-credit-engine"

include("dominio", "aplicacao", "app")
