package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.Goal
import org.springframework.data.jpa.repository.JpaRepository

interface GoalRepository : JpaRepository<Goal, String> {
    fun findByUserId(userId: Long): List<Goal>
}
