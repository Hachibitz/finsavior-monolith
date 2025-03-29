package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
) {

    fun getUserByContext(): User {
        val authentication = SecurityContextHolder.getContext().authentication
        val user: User? = userRepository.findByUsername(authentication.name)
        return user ?: throw RuntimeException("User not found")
    }
}