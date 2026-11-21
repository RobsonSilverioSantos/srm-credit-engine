package br.com.srm.creditengine.cadastro.dominio

// Comparação conservadora de legado, NÃO validação nem atribuição de país/tipo.
// Idêntica a srm_chave_documento(text) na V5: uppercase ASCII, retém apenas A–Z/0–9.
fun chaveComparacaoDocumento(numero: String): String =
    numero.map { if (it in 'a'..'z') it - 32 else it }
        .filter { it in 'A'..'Z' || it in '0'..'9' }.joinToString("")
