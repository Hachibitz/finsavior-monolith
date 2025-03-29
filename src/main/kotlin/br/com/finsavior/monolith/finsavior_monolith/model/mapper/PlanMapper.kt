package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Plan

fun Plan.toPlanDTO(): PlanDTO {
    return PlanDTO(
        planId = this.id,
        planDs = this.description
    )
}