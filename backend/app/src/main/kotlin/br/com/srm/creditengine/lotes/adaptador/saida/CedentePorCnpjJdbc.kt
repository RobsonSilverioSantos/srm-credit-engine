package br.com.srm.creditengine.lotes.adaptador.saida

import br.com.srm.creditengine.lotes.aplicacao.porta.saida.BuscarCedentePorCnpj
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class CedentePorCnpjJdbc(private val jdbc: JdbcTemplate) : BuscarCedentePorCnpj {
    override fun buscar(organizacaoId: UUID, cnpjCanonico: String): UUID? =
        jdbc.query(
            """SELECT id FROM cedentes
               WHERE organizacao_id = ? AND pais = 'BR' AND tipo_documento = 'CNPJ'
                 AND documento_canonico = ? AND ativo = true
                 AND NOT EXISTS (
                     SELECT 1 FROM cedentes_documentos_reservas r
                     WHERE r.organizacao_id = ? AND r.chave = ? AND r.legado_id IS NOT NULL
                 )
               LIMIT 2""",
            RowMapper { rs, _ -> rs.getObject(1, UUID::class.java) },
            organizacaoId, cnpjCanonico, organizacaoId, cnpjCanonico,
        ).singleOrNull()
}
