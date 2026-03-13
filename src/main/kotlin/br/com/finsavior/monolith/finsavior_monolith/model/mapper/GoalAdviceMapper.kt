package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.GoalAdviceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.GoalAdviceHistory

fun GoalAdviceHistory.toDTO(): GoalAdviceDTO = GoalAdviceDTO(
    id = this.id,
    goalId = this.goal.id,
    advice = this.advice,
    createdAt = this.createdAt,
    usedCoins = this.usedCoins
)
