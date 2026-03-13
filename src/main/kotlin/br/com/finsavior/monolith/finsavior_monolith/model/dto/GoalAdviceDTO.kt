package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.time.LocalDateTime

data class GoalAdviceDTO(
    val id: Long?,
    val goalId: String,
    val advice: String,
    val createdAt: LocalDateTime,
    val usedCoins: Boolean
)
