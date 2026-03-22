package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Plan
import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserPlan

fun Plan.toPlanDTO(): PlanDTO {
    return PlanDTO(
        planId = this.id,
        planDs = this.description
    )
}

fun UserPlan.toPlanDTO(): PlanDTO {
    return PlanDTO(
        planId = this.plan.id,
        planDs = this.plan.description,
        subscriptionStatus = this.subscriptionStatus
    )
}
