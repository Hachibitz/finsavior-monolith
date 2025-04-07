package br.com.finsavior.monolith.finsavior_monolith.model.enums.sealed

import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum

sealed class SubscriptionUpdateType {
    data class ChangePlan(val newPlan: PlanTypeEnum) : SubscriptionUpdateType()
    object CancelImmediately : SubscriptionUpdateType()
    object CancelAtPeriodEnd : SubscriptionUpdateType()
}