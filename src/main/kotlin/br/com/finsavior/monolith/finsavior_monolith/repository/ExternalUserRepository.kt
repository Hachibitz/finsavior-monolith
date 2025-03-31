package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.ExternalUser
import org.springframework.data.jpa.repository.JpaRepository

interface ExternalUserRepository : JpaRepository<ExternalUser, Long> {
    fun deleteByUserId(userId: Long)
}