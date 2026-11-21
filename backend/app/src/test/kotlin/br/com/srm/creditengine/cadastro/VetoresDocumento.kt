package br.com.srm.creditengine.cadastro

object VetoresDocumento {
    // Receita: manual-dv-cnpj.pdf pp. 3–4 e codigos-cnpj.zip/src/typescript/test.ts.
    // Valores fixos de referência, não obtidos pelo algoritmo sob teste.
    val oficiais = listOf(
        "12ABC34501DE35", "90021382000122", "90024778000123", "90025108000121",
        "90025255000100", "90024420000109", "90024781000147", "04740714000197",
        "44108058000129", "90024780000100", "90024779000178", "00000000000191",
        "ABCDEFGHIJKL80", "1345C3A5000106",
    )

    val malformados = listOf(
        "", " ", "12ABC34501DE3", "12ABC34501DE355", "12ABC34501DE3A", "12ABC34501DEA5",
        "12.ABC345/01DE-35", "12ABC345/01DE35", "12-ABC-345-01DE-35",
        " 12ABC34501DE35", "12ABC34501DE35 ", "12ABC34501DE35\n", "12ABC34501DE35\r\n",
        "12ABC34501DE\u000035", "１２ABC34501DE35", "12ÁBC34501DE35", "12АBC34501DE35",
        "12ABC34501DK35", "12ABC34501Dſ35", "12ABC34501Dß35", "12ABC 4501DE35",
    )

    fun comMascara(cnpj: String): String =
        "${cnpj.take(2)}.${cnpj.substring(2, 5)}.${cnpj.substring(5, 8)}/${cnpj.substring(8, 12)}-${cnpj.takeLast(2)}"

    // Oráculo sintético independente: pesos fixos por posição, não usa o validador produtivo.
    fun sintetico(base: String): String {
        val pesos1 = listOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val pesos2 = listOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        fun dv(valor: String, pesos: List<Int>): Int {
            val resto = valor.indices.sumOf { (valor[it].code - '0'.code) * pesos[it] } % 11
            return if (resto <= 1) 0 else 11 - resto
        }
        val primeiro = dv(base, pesos1)
        return base + primeiro + dv(base + primeiro, pesos2)
    }
}
