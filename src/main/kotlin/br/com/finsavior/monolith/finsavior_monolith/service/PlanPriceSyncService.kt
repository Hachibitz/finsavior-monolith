package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.properties.PlanIdProperties
import br.com.finsavior.monolith.finsavior_monolith.integration.client.StripeClient
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripePrice
import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanDisplayPriceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.PlanDisplayPrice
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanDisplayPriceRepository
import com.google.api.services.androidpublisher.model.Subscription
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.Currency
import java.util.concurrent.ConcurrentHashMap

@Service
class PlanPriceSyncService(
    private val stripeClient: StripeClient,
    private val googlePlayBillingService: GooglePlayBillingService,
    private val planIdProperties: PlanIdProperties,
    private val repository: PlanDisplayPriceRepository,
    @param:Value("\${finsavior.features.i18n-enabled:true}") private val i18nEnabled: Boolean = true,
    @param:Value("\${finsavior.features.stripe-enabled:false}") private val stripeEnabled: Boolean = false
) {
    private val log = KotlinLogging.logger {}

    private data class CachedLiveResult(
        val fetchedAt: Instant,
        val prices: List<PlanDisplayPriceDTO>
    )

    private val shortLiveCache = ConcurrentHashMap<String, CachedLiveResult>()
    private val liveCacheTtl = Duration.ofMinutes(5)

    companion object {
        const val PROVIDER_STRIPE = "STRIPE"
        const val PROVIDER_GOOGLE_PLAY = "GOOGLE_PLAY"
        const val SOURCE_PROVIDER_LIVE = "PROVIDER_LIVE"
        const val SOURCE_PROVIDER_CACHE = "PROVIDER_CACHE"

        private val PREFERRED_REGION_BY_CURRENCY = mapOf(
            "BRL" to "BR",
            "USD" to "US",
            "EUR" to "DE",
            "GBP" to "GB",
            "JPY" to "JP",
            "MXN" to "MX",
            "KRW" to "KR",
            "INR" to "IN",
            "IDR" to "ID"
        )
    }

    /**
     * Fetches live prices for a provider + currency (short in-memory TTL to avoid
     * hitting provider rate limits on every screen load). Returns null if the
     * provider fails or is not configured so the caller can fall back to DB cache.
     */
    fun fetchLivePrices(provider: String, currency: String): List<PlanDisplayPriceDTO>? {
        val normalizedProvider = provider.uppercase()
        val normalizedCurrency = currency.uppercase()
        val cacheKey = "$normalizedProvider:$normalizedCurrency"
        val cached = shortLiveCache[cacheKey]
        if (cached != null && Duration.between(cached.fetchedAt, Instant.now()) < liveCacheTtl) {
            return cached.prices
        }

        val liveAll = when (normalizedProvider) {
            PROVIDER_STRIPE -> fetchAllStripePricesSafe()
            PROVIDER_GOOGLE_PLAY -> fetchAllPlayPricesSafe()
            else -> return null
        } ?: return null

        val now = LocalDateTime.now()
        val filtered = liveAll
            .filter { it.currency.equals(normalizedCurrency, ignoreCase = true) }
            .map { it.copy(source = SOURCE_PROVIDER_LIVE, lastSyncedAt = now) }

        if (filtered.isNotEmpty()) {
            shortLiveCache[cacheKey] = CachedLiveResult(Instant.now(), filtered)
        }
        return filtered.ifEmpty { null }
    }

    @Scheduled(cron = "\${finsavior.prices.sync-cron:0 0 4 * * *}")
    @Transactional
    fun syncAllProviderPrices(): Map<String, Int> {
        val now = LocalDateTime.now()
        var stripeSaved = 0
        var playSaved = 0

        if (stripeEnabled) {
            fetchAllStripePricesSafe()?.let { list ->
                stripeSaved = persistProviderPrices(list, now)
            }
        }
        fetchAllPlayPricesSafe()?.let { list ->
            playSaved = persistProviderPrices(list, now)
        }

        shortLiveCache.clear()
        log.info { "Provider price sync finished: stripe=$stripeSaved googlePlay=$playSaved" }
        return mapOf("stripe" to stripeSaved, "googlePlay" to playSaved)
    }

    private fun persistProviderPrices(items: List<PlanDisplayPriceDTO>, syncedAt: LocalDateTime): Int {
        val scopedItems = if (i18nEnabled) items else items.filter { it.currency.equals("BRL", ignoreCase = true) }
        var count = 0
        scopedItems.forEach { item ->
            val existing = repository.findByPlanTierAndBillingCycleAndCurrencyIgnoreCaseAndProviderIgnoreCase(
                item.planTier,
                item.billingCycle,
                item.currency,
                item.provider
            )
            if (existing != null) {
                existing.amount = item.amount
                existing.source = SOURCE_PROVIDER_CACHE
                existing.externalPriceId = item.externalPriceId
                existing.lastSyncedAt = syncedAt
                repository.save(existing)
            } else {
                repository.save(
                    PlanDisplayPrice(
                        planTier = item.planTier,
                        billingCycle = item.billingCycle,
                        currency = item.currency,
                        provider = item.provider,
                        source = SOURCE_PROVIDER_CACHE,
                        externalPriceId = item.externalPriceId,
                        lastSyncedAt = syncedAt,
                        amount = item.amount,
                        audit = Audit()
                    )
                )
            }
            count++
        }
        return count
    }

    internal fun fetchAllStripePricesSafe(): List<PlanDisplayPriceDTO>? {
        val mappings = listOf(
            Triple("BASIC", "MONTHLY", PlanTypeEnum.STRIPE_BASIC_MONTHLY.id),
            Triple("BASIC", "ANNUAL", PlanTypeEnum.STRIPE_BASIC_ANNUAL.id),
            Triple("PLUS", "MONTHLY", PlanTypeEnum.STRIPE_PLUS_MONTHLY.id),
            Triple("PLUS", "ANNUAL", PlanTypeEnum.STRIPE_PLUS_ANNUAL.id),
            Triple("PREMIUM", "MONTHLY", PlanTypeEnum.STRIPE_PREMIUM_MONTHLY.id),
            Triple("PREMIUM", "ANNUAL", PlanTypeEnum.STRIPE_PREMIUM_ANNUAL.id),
        ).filter { it.third.isNotBlank() && it.third != "UNSET" }

        if (mappings.isEmpty()) return null

        val collected = mutableListOf<PlanDisplayPriceDTO>()
        var anySuccess = false

        mappings.forEach { (tier, cycle, productId) ->
            val expectedInterval = if (cycle == "ANNUAL") "year" else "month"
            try {
                val prices = stripeClient.getPricesByProduct(productId, active = true).data
                anySuccess = true
                val matchingPrice = selectStripePriceForCycle(prices, expectedInterval)
                if (matchingPrice != null) {
                    collected.addAll(extractStripeCurrencyAmounts(tier, cycle, matchingPrice))
                }
            } catch (e: Exception) {
                log.warn(e) { "Failed to fetch Stripe prices for product=$productId" }
            }
        }

        return if (anySuccess && collected.isNotEmpty()) collected else null
    }

    internal fun selectStripePriceForCycle(prices: List<StripePrice>, expectedInterval: String): StripePrice? =
        prices
            .filter { it.active != false }
            .firstOrNull { it.recurring?.interval.equals(expectedInterval, ignoreCase = true) }
            ?: prices.firstOrNull { it.active != false }

    internal fun extractStripeCurrencyAmounts(
        tier: String,
        cycle: String,
        price: StripePrice
    ): List<PlanDisplayPriceDTO> {
        val results = mutableMapOf<String, PlanDisplayPriceDTO>()

        if (price.unit_amount != null && price.currency.isNotBlank()) {
            val code = price.currency.uppercase()
            results[code] = PlanDisplayPriceDTO(
                planTier = tier,
                billingCycle = cycle,
                currency = code,
                amount = minorUnitsToBigDecimal(price.unit_amount, code),
                provider = PROVIDER_STRIPE,
                source = SOURCE_PROVIDER_LIVE,
                externalPriceId = price.id
            )
        }

        price.currency_options?.forEach { (rawCode, option) ->
            val unitAmount = option.unit_amount ?: return@forEach
            val code = rawCode.uppercase()
            results[code] = PlanDisplayPriceDTO(
                planTier = tier,
                billingCycle = cycle,
                currency = code,
                amount = minorUnitsToBigDecimal(unitAmount, code),
                provider = PROVIDER_STRIPE,
                source = SOURCE_PROVIDER_LIVE,
                externalPriceId = price.id
            )
        }

        return results.values.toList()
    }

    internal fun fetchAllPlayPricesSafe(): List<PlanDisplayPriceDTO>? {
        val subscriptions = googlePlayBillingService.listSubscriptionsForPricing()
        if (subscriptions.isEmpty()) return null
        return extractPlayPricesFromSubscriptions(subscriptions).ifEmpty { null }
    }

    internal fun extractPlayPricesFromSubscriptions(subscriptions: List<Subscription>): List<PlanDisplayPriceDTO> {
        val skuTargets = listOf(
            Triple("BASIC", "MONTHLY", planIdProperties.PLAY_BASIC_MONTHLY to planIdProperties.PLAY_BASIC_MONTHLY_BASE_PLAN),
            Triple("BASIC", "ANNUAL", planIdProperties.PLAY_BASIC_ANNUAL to planIdProperties.PLAY_BASIC_ANNUAL_BASE_PLAN),
            Triple("PLUS", "MONTHLY", planIdProperties.PLAY_PLUS_MONTHLY to planIdProperties.PLAY_PLUS_MONTHLY_BASE_PLAN),
            Triple("PLUS", "ANNUAL", planIdProperties.PLAY_PLUS_ANNUAL to planIdProperties.PLAY_PLUS_ANNUAL_BASE_PLAN),
            Triple("PREMIUM", "MONTHLY", planIdProperties.PLAY_PREMIUM_MONTHLY to planIdProperties.PLAY_PREMIUM_MONTHLY_BASE_PLAN),
            Triple("PREMIUM", "ANNUAL", planIdProperties.PLAY_PREMIUM_ANNUAL to planIdProperties.PLAY_PREMIUM_ANNUAL_BASE_PLAN)
        )

        val byProductId = subscriptions.associateBy { it.productId }
        val results = mutableListOf<PlanDisplayPriceDTO>()

        skuTargets.forEach { (tier, cycle, sku) ->
            val (productId, basePlanId) = sku
            val subscription = byProductId[productId] ?: return@forEach
            val basePlan = subscription.basePlans?.firstOrNull { it.basePlanId == basePlanId } ?: return@forEach
            val configs = basePlan.regionalConfigs ?: return@forEach

            val byCurrency = mutableMapOf<String, Pair<String, BigDecimal>>()
            configs.forEach { regional ->
                val price = regional.price ?: return@forEach
                val currencyCode = price.currencyCode?.uppercase() ?: return@forEach
                val regionCode = regional.regionCode?.uppercase() ?: ""
                val units = price.units ?: 0L
                val nanos = price.nanos ?: 0
                val amount = moneyToBigDecimal(units, nanos, currencyCode)

                val preferredRegion = PREFERRED_REGION_BY_CURRENCY[currencyCode]
                val existing = byCurrency[currencyCode]
                if (existing == null || regionCode == preferredRegion) {
                    byCurrency[currencyCode] = regionCode to amount
                }
            }

            byCurrency.forEach { (currencyCode, pair) ->
                results.add(
                    PlanDisplayPriceDTO(
                        planTier = tier,
                        billingCycle = cycle,
                        currency = currencyCode,
                        amount = pair.second,
                        provider = PROVIDER_GOOGLE_PLAY,
                        source = SOURCE_PROVIDER_LIVE,
                        externalPriceId = "$productId:$basePlanId"
                    )
                )
            }
        }

        return results
    }

    internal fun minorUnitsToBigDecimal(minorUnits: Long, currencyCode: String): BigDecimal {
        val fractionDigits = runCatching {
            Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        }.getOrDefault(2)
        return BigDecimal.valueOf(minorUnits, fractionDigits)
            .setScale(2, RoundingMode.HALF_UP)
    }

    internal fun moneyToBigDecimal(units: Long, nanos: Int, currencyCode: String): BigDecimal {
        val fractionDigits = runCatching {
            Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        }.getOrDefault(2)
        val raw = BigDecimal.valueOf(units).add(BigDecimal.valueOf(nanos.toLong(), 9))
        return raw.setScale(fractionDigits, RoundingMode.HALF_UP)
            .setScale(2, RoundingMode.HALF_UP)
    }
}
