package br.com.finsavior.monolith.finsavior_monolith.config.properties

import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.PlanDisplayPrice
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanDisplayPriceRepository
import jakarta.annotation.PostConstruct
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Seeds display prices used by the Plans screen (not store billing amounts).
 * Existing rows are left untouched so admin edits persist across restarts.
 */
@Component
class PlanDisplayPriceInitializer(
    private val repository: PlanDisplayPriceRepository
) {
    private val log = KotlinLogging.logger {}

    @PostConstruct
    fun seedIfEmpty() {
        if (repository.count() > 0) {
            log.info { "plan_display_price already seeded (${repository.count()} rows)" }
            return
        }
        val seeds = buildList {
            // BRL (anchor)
            add(seed("BASIC", "MONTHLY", "BRL", "7.90"))
            add(seed("BASIC", "ANNUAL", "BRL", "79.00"))
            add(seed("PLUS", "MONTHLY", "BRL", "19.90"))
            add(seed("PLUS", "ANNUAL", "BRL", "199.90"))
            add(seed("PREMIUM", "MONTHLY", "BRL", "39.90"))
            add(seed("PREMIUM", "ANNUAL", "BRL", "399.00"))
            // USD
            add(seed("BASIC", "MONTHLY", "USD", "1.49"))
            add(seed("BASIC", "ANNUAL", "USD", "14.90"))
            add(seed("PLUS", "MONTHLY", "USD", "3.99"))
            add(seed("PLUS", "ANNUAL", "USD", "39.90"))
            add(seed("PREMIUM", "MONTHLY", "USD", "7.99"))
            add(seed("PREMIUM", "ANNUAL", "USD", "79.90"))
            // EUR
            add(seed("BASIC", "MONTHLY", "EUR", "1.49"))
            add(seed("BASIC", "ANNUAL", "EUR", "14.90"))
            add(seed("PLUS", "MONTHLY", "EUR", "3.99"))
            add(seed("PLUS", "ANNUAL", "EUR", "39.90"))
            add(seed("PREMIUM", "MONTHLY", "EUR", "7.99"))
            add(seed("PREMIUM", "ANNUAL", "EUR", "79.90"))
            // GBP
            add(seed("BASIC", "MONTHLY", "GBP", "1.29"))
            add(seed("BASIC", "ANNUAL", "GBP", "12.90"))
            add(seed("PLUS", "MONTHLY", "GBP", "3.49"))
            add(seed("PLUS", "ANNUAL", "GBP", "34.90"))
            add(seed("PREMIUM", "MONTHLY", "GBP", "6.99"))
            add(seed("PREMIUM", "ANNUAL", "GBP", "69.90"))
            // JPY (whole yen)
            add(seed("BASIC", "MONTHLY", "JPY", "220"))
            add(seed("BASIC", "ANNUAL", "JPY", "2200"))
            add(seed("PLUS", "MONTHLY", "JPY", "590"))
            add(seed("PLUS", "ANNUAL", "JPY", "5900"))
            add(seed("PREMIUM", "MONTHLY", "JPY", "1190"))
            add(seed("PREMIUM", "ANNUAL", "JPY", "11900"))
            // MXN
            add(seed("BASIC", "MONTHLY", "MXN", "29.00"))
            add(seed("BASIC", "ANNUAL", "MXN", "290.00"))
            add(seed("PLUS", "MONTHLY", "MXN", "79.00"))
            add(seed("PLUS", "ANNUAL", "MXN", "790.00"))
            add(seed("PREMIUM", "MONTHLY", "MXN", "159.00"))
            add(seed("PREMIUM", "ANNUAL", "MXN", "1590.00"))
        }
        repository.saveAll(seeds)
        log.info { "Seeded ${seeds.size} plan_display_price rows" }
    }

    private fun seed(tier: String, cycle: String, currency: String, amount: String) =
        PlanDisplayPrice(
            planTier = tier,
            billingCycle = cycle,
            currency = currency,
            amount = BigDecimal(amount),
            audit = Audit()
        )
}
