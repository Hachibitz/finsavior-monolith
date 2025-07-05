package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserFsCoin
import org.springframework.data.jpa.repository.JpaRepository

interface UserFsCoinRepository : JpaRepository<UserFsCoin, Long> {
    fun findByUserId(userId: Long): UserFsCoin?
}