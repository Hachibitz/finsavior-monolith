package br.com.finsavior.monolith.finsavior_monolith.security

import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import java.util.stream.Collectors

class CustomUserDetails(private val user: User) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> {
        return user.roles.stream()
            .map { role -> SimpleGrantedAuthority(role.name) }
            .collect(Collectors.toList())
    }

    fun getId(): Long {
        return user.id
    }

    override fun getPassword(): String {
        return user.password
    }

    override fun getUsername(): String {
        return user.username
    }

    override fun isAccountNonExpired(): Boolean {
        return true
    }

    override fun isAccountNonLocked(): Boolean {
        return true
    }

    override fun isCredentialsNonExpired(): Boolean {
        return true
    }

    override fun isEnabled(): Boolean {
        return true
    }
}