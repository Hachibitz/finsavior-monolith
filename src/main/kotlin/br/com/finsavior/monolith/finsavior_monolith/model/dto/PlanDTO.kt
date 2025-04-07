package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum

data class PlanDTO (
    val planId: String,
    val planDs: PlanTypeEnum
)