package br.com.finsavior.monolith.finsavior_monolith.config.properties

import br.com.finsavior.monolith.finsavior_monolith.repository.PlanDisplayPriceRepository
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.stereotype.Component

/**
 * Does not seed hardcoded prices — Stripe and Google Play APIs are the source of truth
 * and populate the DB cache via the daily sync job.
 */
@Component
class PlanDisplayPriceInitializer(
    private val repository: PlanDisplayPriceRepository
) {
    private val log = KotlinLogging.logger {}

    @PostConstruct
    fun logCacheStatus() {
        log.info { "plan_display_price cache rows at startup: ${repository.count()}" }
    }
}
