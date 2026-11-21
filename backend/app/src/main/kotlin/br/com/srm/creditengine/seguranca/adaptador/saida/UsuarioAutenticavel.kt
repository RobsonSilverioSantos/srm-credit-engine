package br.com.srm.creditengine.seguranca.adaptador.saida

import br.com.srm.creditengine.usuarios.dominio.PerfilUsuario
import java.util.UUID
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

class UsuarioAutenticavel(
    val id: UUID,
    val organizacaoId: UUID,
    private val email: String,
    private val senhaHash: String,
    private val perfil: PerfilUsuario,
    private val habilitado: Boolean,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_USER"), SimpleGrantedAuthority("ROLE_${perfil.name}"))

    override fun getPassword(): String = senhaHash

    override fun getUsername(): String = email

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = habilitado
}
