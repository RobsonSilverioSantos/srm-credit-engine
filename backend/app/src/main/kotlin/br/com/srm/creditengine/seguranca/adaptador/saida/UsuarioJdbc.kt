package br.com.srm.creditengine.seguranca.adaptador.saida

import java.sql.ResultSet
import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

class UsuarioAutenticavel(
    val id: UUID,
    val organizacaoId: UUID,
    private val email: String,
    private val senhaHash: String,
    private val habilitado: Boolean,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_USER"))

    override fun getPassword(): String = senhaHash

    override fun getUsername(): String = email

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = habilitado
}

@Service
class UsuarioJdbc(
    private val jdbc: JdbcTemplate,
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val emailNormalizado = username.trim()
        val usuario = jdbc.query(
            """
            SELECT u.id, u.organizacao_id, u.email, u.senha_hash, u.ativo, o.ativa AS organizacao_ativa
            FROM usuarios u
            JOIN organizacoes o ON o.id = u.organizacao_id
            WHERE lower(u.email) = lower(?)
            """.trimIndent(),
            mapper,
            emailNormalizado,
        ).singleOrNull() ?: throw UsernameNotFoundException("Usuário não encontrado")

        if (!usuario.isEnabled) throw UsernameNotFoundException("Usuário não encontrado")
        return usuario
    }

    private val mapper = RowMapper { rs: ResultSet, _: Int ->
        UsuarioAutenticavel(
            rs.getObject("id", UUID::class.java),
            rs.getObject("organizacao_id", UUID::class.java),
            rs.getString("email"),
            rs.getString("senha_hash"),
            rs.getBoolean("ativo") && rs.getBoolean("organizacao_ativa"),
        )
    }
}
