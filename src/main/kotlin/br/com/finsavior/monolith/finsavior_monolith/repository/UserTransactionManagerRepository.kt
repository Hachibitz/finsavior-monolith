package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserTransactionManager
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserTransactionManagerRepository : JpaRepository<UserTransactionManager, Long> {
    fun findByUserId(userId: Long): UserTransactionManager?
}