package br.com.srm.creditengine.cadastro.adaptador.saida

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Cedentes
import br.com.srm.creditengine.cadastro.dominio.Cedente
import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class CedentesJdbc(private val jdbc: JdbcTemplate) : Cedentes {
    override fun cadastrar(cedente: Cedente): Cedente {
        require(cedente.ativo) { "Um novo cedente deve estar ativo" }
        jdbc.update(
            "INSERT INTO cedentes (id, organizacao_id, nome, documento) VALUES (?, ?, ?, ?)",
            cedente.id, cedente.organizacaoId, cedente.nome, cedente.documento,
        )
        return cedente
    }

    override fun buscar(organizacaoId: UUID, id: UUID): Cedente? =
        jdbc.query("SELECT * FROM cedentes WHERE organizacao_id = ? AND id = ?", mapper, organizacaoId, id)
            .singleOrNull()

    override fun listar(organizacaoId: UUID): List<Cedente> =
        jdbc.query("SELECT * FROM cedentes WHERE organizacao_id = ? ORDER BY id", mapper, organizacaoId)

    private val mapper = RowMapper { rs: ResultSet, _: Int ->
        Cedente(
            rs.getObject("id", UUID::class.java), rs.getObject("organizacao_id", UUID::class.java),
            rs.getString("nome"), rs.getString("documento"), rs.getBoolean("ativo"),
        )
    }
}
