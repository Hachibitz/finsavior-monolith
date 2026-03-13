package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.GoalAdviceHistory
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface GoalAdviceHistoryRepository : JpaRepository<GoalAdviceHistory, Long> {
    fun countByUserIdAndCreatedAtBetweenAndUsedCoinsIsFalse(userId: Long, start: LocalDateTime, end: LocalDateTime): Int
    fun findByGoalIdAndUserId(goalId: String, userId: Long): List<GoalAdviceHistory>
}
