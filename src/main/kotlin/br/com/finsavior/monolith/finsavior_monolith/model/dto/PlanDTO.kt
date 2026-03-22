package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.SubscriptionStatusEnum

data class PlanDTO (
    val planId: String,
    val planDs: PlanTypeEnum,
    val subscriptionStatus: SubscriptionStatusEnum? = null
)
