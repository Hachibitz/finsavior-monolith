package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.math.BigDecimal

data class GoalDTO(
    val id: String?,
    val name: String,
    val targetAmount: BigDecimal,
    val currentAmount: BigDecimal,
    val deadline: String, // ISO date
    val category: String?
)
