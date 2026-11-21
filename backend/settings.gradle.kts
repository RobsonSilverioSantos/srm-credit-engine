pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "srm-credit-engine"

include("dominio", "aplicacao", "app")
