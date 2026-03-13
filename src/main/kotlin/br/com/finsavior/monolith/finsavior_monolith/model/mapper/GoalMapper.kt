package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.GoalDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Goal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Goal.toDTO(): GoalDTO = GoalDTO(
    id = this.id,
    name = this.name,
    targetAmount = this.targetAmount,
    currentAmount = this.currentAmount,
    deadline = this.deadline.format(DateTimeFormatter.ISO_DATE),
    category = this.category
)

fun GoalDTO.toEntity(user: br.com.finsavior.monolith.finsavior_monolith.model.entity.User): Goal = Goal(
    id = this.id ?: java.util.UUID.randomUUID().toString(),
    name = this.name,
    targetAmount = this.targetAmount,
    currentAmount = this.currentAmount,
    deadline = LocalDate.parse(this.deadline, DateTimeFormatter.ISO_DATE),
    category = this.category,
    user = user
)
