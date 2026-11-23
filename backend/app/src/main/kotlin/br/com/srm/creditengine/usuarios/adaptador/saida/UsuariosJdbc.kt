package br.com.srm.creditengine.usuarios.adaptador.saida

import br.com.srm.creditengine.usuarios.aplicacao.porta.saida.RepositorioUsuarios
import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import br.com.srm.creditengine.usuarios.dominio.UsuarioOrganizacional
import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper

class UsuariosJdbc(
    private val jdbc: JdbcTemplate,
) : RepositorioUsuarios {
    override fun buscar(organizacaoId: UUID, usuarioId: UUID): UsuarioOrganizacional? =
        jdbc.query(
            """SELECT id, organizacao_id, email, perfil, ativo
               FROM usuarios WHERE organizacao_id = ? AND id = ?""",
            mapper,
            organizacaoId,
            usuarioId,
        ).singleOrNull()

    override fun listar(
        organizacaoId: UUID,
        perfis: Set<PerfilUsuario>,
    ): List<UsuarioOrganizacional> {
        if (perfis.isEmpty()) return emptyList()
        val placeholders = perfis.joinToString(", ") { "?" }
        val parametros = arrayOf<Any>(organizacaoId, *perfis.map { it.name }.toTypedArray())
        return jdbc.query(
            """SELECT id, organizacao_id, email, perfil, ativo
               FROM usuarios
               WHERE organizacao_id = ? AND perfil IN ($placeholders)
               ORDER BY lower(email), id""",
            mapper,
            *parametros,
        )
    }

    override fun inserir(
        usuario: UsuarioOrganizacional,
        senhaHash: String,
    ): UsuarioOrganizacional {
        jdbc.update(
            """INSERT INTO usuarios (id, organizacao_id, email, senha_hash, perfil)
               VALUES (?, ?, ?, ?, ?)""",
            usuario.id,
            usuario.organizacaoId,
            usuario.email,
            senhaHash,
            usuario.perfil.name,
        )
        return usuario
    }

    override fun provisionarMaster(
        usuario: UsuarioOrganizacional,
        senhaHash: String,
    ): UsuarioOrganizacional? =
        jdbc.query(
            """INSERT INTO usuarios (id, organizacao_id, email, senha_hash, perfil)
               SELECT ?, o.id, ?, ?, 'MASTER'
               FROM organizacoes o
               WHERE o.id = ? AND o.ativa = TRUE
                 AND NOT EXISTS (
                     SELECT 1 FROM usuarios existente
                     WHERE existente.organizacao_id = o.id AND existente.perfil = 'MASTER'
                 )
               RETURNING id, organizacao_id, email, perfil, ativo""",
            mapper,
            usuario.id,
            usuario.email,
            senhaHash,
            usuario.organizacaoId,
        ).singleOrNull()

    override fun organizacaoAtiva(organizacaoId: UUID): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM organizacoes WHERE id = ? AND ativa = TRUE)",
            Boolean::class.java,
            organizacaoId,
        ) == true

    override fun existeMaster(organizacaoId: UUID): Boolean =
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM usuarios WHERE organizacao_id = ? AND perfil = 'MASTER')",
            Boolean::class.java,
            organizacaoId,
        ) == true

    override fun alterarStatus(
        organizacaoId: UUID,
        usuarioId: UUID,
        perfil: PerfilUsuario,
        ativo: Boolean,
    ): Boolean =
        jdbc.update(
            """UPDATE usuarios SET ativo = ?, atualizado_em = CURRENT_TIMESTAMP
               WHERE organizacao_id = ? AND id = ? AND perfil = ?""",
            ativo,
            organizacaoId,
            usuarioId,
            perfil.name,
        ) == 1

    private val mapper = RowMapper { rs: ResultSet, _: Int ->
        UsuarioOrganizacional(
            id = rs.getObject("id", UUID::class.java),
            organizacaoId = rs.getObject("organizacao_id", UUID::class.java),
            email = rs.getString("email"),
            perfil = PerfilUsuario.valueOf(rs.getString("perfil")),
            ativo = rs.getBoolean("ativo"),
        )
    }
}
