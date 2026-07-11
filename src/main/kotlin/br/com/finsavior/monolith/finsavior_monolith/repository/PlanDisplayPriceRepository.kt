package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.PlanDisplayPrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PlanDisplayPriceRepository : JpaRepository<PlanDisplayPrice, Long> {
    fun findByCurrencyIgnoreCase(currency: String): List<PlanDisplayPrice>
    fun findByPlanTierAndBillingCycleAndCurrencyIgnoreCase(
        planTier: String,
        billingCycle: String,
        currency: String
    ): PlanDisplayPrice?
    fun findAllByOrderByCurrencyAscPlanTierAscBillingCycleAsc(): List<PlanDisplayPrice>
}
