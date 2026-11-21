package br.com.srm.creditengine.cadastro.dominio

class ValidadorCnpj : ValidadorDocumento {
    override fun normalizarEValidar(numero: String): String {
        require(numero.matches(SEM_MASCARA) || numero.matches(COM_MASCARA)) { "Formato de CNPJ inválido" }
        val canonico = numero.filter { it != '.' && it != '/' && it != '-' }
            .map { if (it in 'a'..'z') it - 32 else it }.joinToString("")
        require(canonico.toSet().size > 1) { "CNPJ repetido inválido" }
        val base = canonico.take(12)
        val primeiro = dv(base)
        val segundo = dv(base + primeiro)
        require(canonico.takeLast(2) == "$primeiro$segundo") { "Dígitos verificadores de CNPJ inválidos" }
        return canonico
    }

    // Receita Federal, Manual DV, pp. 3–4: ASCII - 48, pesos 2..9 da direita, módulo 11.
    private fun dv(base: String): Int {
        val resto = base.reversed().mapIndexed { indice, caractere ->
            (caractere.code - 48) * (2 + indice % 8)
        }.sum() % 11
        return if (resto < 2) 0 else 11 - resto
    }

    private companion object {
        val SEM_MASCARA = Regex("[A-Za-z0-9]{12}[0-9]{2}")
        val COM_MASCARA = Regex("[A-Za-z0-9]{2}\\.[A-Za-z0-9]{3}\\.[A-Za-z0-9]{3}/[A-Za-z0-9]{4}-[0-9]{2}")
    }
}
