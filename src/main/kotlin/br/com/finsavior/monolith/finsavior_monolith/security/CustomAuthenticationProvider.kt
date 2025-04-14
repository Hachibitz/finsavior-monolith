package br.com.finsavior.monolith.finsavior_monolith.security

import br.com.finsavior.monolith.finsavior_monolith.model.enums.FlagEnum
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

        if (user.audit?.delFg == FlagEnum.N) {
            throw BadCredentialsException("Conta não ativada. Verifique seu e-mail para ativar a conta.")
        }

        val authorities: List<GrantedAuthority> = user.roles!!.map { SimpleGrantedAuthority(it.name) }

        return UsernamePasswordAuthenticationToken(username, password, authorities)
    }

    override fun supports(authentication: Class<*>): Boolean {
        return authentication == UsernamePasswordAuthenticationToken::class.java
    }
}
