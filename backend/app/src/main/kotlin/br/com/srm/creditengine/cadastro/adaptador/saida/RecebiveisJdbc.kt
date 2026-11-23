package br.com.srm.creditengine.cadastro.adaptador.saida

import br.com.srm.creditengine.cadastro.aplicacao.porta.saida.Recebiveis
import br.com.srm.creditengine.cadastro.dominio.Recebivel
import br.com.srm.creditengine.cadastro.dominio.StatusRecebivel
import java.sql.Date
import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class RecebiveisJdbc(private val jdbc: JdbcTemplate) : Recebiveis {
    override fun cadastrar(recebivel: Recebivel): Recebivel {
        require(recebivel.status == StatusRecebivel.DISPONIVEL && recebivel.versao == 0L) {
            "Um novo recebível deve estar disponível e na versão inicial"
        }
        jdbc.update(
            """INSERT INTO recebiveis
               (id, organizacao_id, cedente_id, modalidade, valor_face_brl, prazo_meses, vencimento)
               VALUES (?, ?, ?, ?, ?, ?, ?)""",
            recebivel.id, recebivel.organizacaoId, recebivel.cedenteId, recebivel.modalidade,
            recebivel.valorFaceBrl, recebivel.prazoMeses, recebivel.vencimento?.let(Date::valueOf),
        )
        return recebivel
    }

    override fun buscar(organizacaoId: UUID, id: UUID): Recebivel? =
        jdbc.query("SELECT * FROM recebiveis WHERE organizacao_id = ? AND id = ?", mapper, organizacaoId, id)
            .singleOrNull()

    override fun listar(organizacaoId: UUID): List<Recebivel> =
        jdbc.query("SELECT * FROM recebiveis WHERE organizacao_id = ? ORDER BY id", mapper, organizacaoId)

    private val mapper = RowMapper { rs: ResultSet, _: Int ->
        Recebivel(
            rs.getObject("id", UUID::class.java), rs.getObject("organizacao_id", UUID::class.java),
            rs.getObject("cedente_id", UUID::class.java), rs.getString("modalidade"),
            rs.getBigDecimal("valor_face_brl"), rs.getInt("prazo_meses"),
            rs.getDate("vencimento")?.toLocalDate(), StatusRecebivel.valueOf(rs.getString("status")),
            rs.getLong("versao"),
        )
    }
}
