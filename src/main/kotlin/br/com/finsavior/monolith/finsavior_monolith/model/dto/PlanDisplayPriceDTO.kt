package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.math.BigDecimal

data class PlanDisplayPriceDTO(
    val id: Long? = null,
    val planTier: String,
    val billingCycle: String,
    val currency: String,
    val amount: BigDecimal
)

data class PlanPricesResponse(
    val currency: String,
    val prices: List<PlanDisplayPriceDTO>
)

data class UpsertPlanDisplayPriceRequest(
    val planTier: String,
    val billingCycle: String,
    val currency: String,
    val amount: BigDecimal
)
