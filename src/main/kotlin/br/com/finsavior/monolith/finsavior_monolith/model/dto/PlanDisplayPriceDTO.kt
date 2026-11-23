package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.math.BigDecimal
import java.time.LocalDateTime

data class PlanDisplayPriceDTO(
    val id: Long? = null,
    val planTier: String,
    val billingCycle: String,
    val currency: String,
    val amount: BigDecimal,
    val provider: String = "MANUAL",
    val source: String = "MANUAL",
    val externalPriceId: String? = null,
    val lastSyncedAt: LocalDateTime? = null
)

data class PlanPricesResponse(
    val currency: String,
    val prices: List<PlanDisplayPriceDTO>,
    val provider: String = "GOOGLE_PLAY",
    val source: String = "UNAVAILABLE",
    val lastSyncedAt: LocalDateTime? = null,
    val i18nEnabled: Boolean = true,
    val stripeEnabled: Boolean = false
)

data class UpsertPlanDisplayPriceRequest(
    val planTier: String,
    val billingCycle: String,
    val currency: String,
    val amount: BigDecimal,
    val provider: String? = null
)
