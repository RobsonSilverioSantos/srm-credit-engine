package br.com.srm.creditengine.usuarios.aplicacao

import java.nio.charset.StandardCharsets
import java.util.Locale

internal fun validarEmail(valor: String): String {
    val email = valor.trim().lowercase(Locale.ROOT)
    require(email.length <= 254 && email.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
        "E-mail inválido"
    }
    return email
}

internal fun validarSenha(senha: String) {
    val tamanhoEmBytes = senha.toByteArray(StandardCharsets.UTF_8).size
    require(tamanhoEmBytes in 12..72) { "A senha deve ter entre 12 e 72 bytes UTF-8" }
}
