package br.com.finsavior.monolith.finsavior_monolith.security

import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserSecurityDetails(private val userRepository: UserRepository) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = userRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("Usuário não encontrado: $username")

        return CustomUserDetails(user)
    }
}