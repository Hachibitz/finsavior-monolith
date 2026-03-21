package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    
    fun findByEmail(email: String): User?
    
    @EntityGraph(attributePaths = ["userProfile", "userPlan", "userPlan.plan"])
    fun findByPhoneNumber(phoneNumber: String): User?
}
