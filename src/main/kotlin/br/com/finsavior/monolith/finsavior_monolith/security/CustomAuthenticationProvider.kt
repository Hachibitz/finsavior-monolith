package br.com.finsavior.monolith.finsavior_monolith.security

import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationProvider(
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder
) : AuthenticationProvider {

    override fun authenticate(authentication: Authentication): Authentication {
        val username = authentication.name
        val password = authentication.credentials.toString()

        val user = userRepository.findByUsername(username) ?: throw BadCredentialsException("Usuário não encontrado")

        if (!passwordEncoder.matches(password, user.password)) {
            throw BadCredentialsException("Credenciais inválidas")
        }

        val authorities: List<GrantedAuthority> = user.roles!!.map { SimpleGrantedAuthority(it.name) }

        return UsernamePasswordAuthenticationToken(username, password, authorities)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return authentication == UsernamePasswordAuthenticationToken::class.java
    }
}
