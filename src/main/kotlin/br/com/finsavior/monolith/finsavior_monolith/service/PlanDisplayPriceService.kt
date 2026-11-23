package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanDisplayPriceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanPricesResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpsertPlanDisplayPriceRequest
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.PlanDisplayPrice
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanDisplayPriceRepository
import br.com.finsavior.monolith.finsavior_monolith.util.LocaleSupport
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Currency

@Service
class PlanDisplayPriceService(
    private val repository: PlanDisplayPriceRepository,
    private val syncService: PlanPriceSyncService? = null,
    @param:Value("\${finsavior.features.i18n-enabled:false}") private val i18nEnabled: Boolean = false
) {
    private val log = KotlinLogging.logger {}

    fun isI18nEnabled(): Boolean = i18nEnabled

    fun getPricesForCurrency(
        currencyParam: String?,
        acceptLanguage: String?,
        providerParam: String? = null
    ): PlanPricesResponse {
        val currency = if (i18nEnabled) resolveCurrency(currencyParam, acceptLanguage) else "BRL"
        val provider = resolveProvider(providerParam)

        val livePrices = syncService?.fetchLivePrices(provider, currency)
        if (!livePrices.isNullOrEmpty()) {
            return PlanPricesResponse(
                currency = currency,
                prices = livePrices,
                provider = provider,
                source = PlanPriceSyncService.SOURCE_PROVIDER_LIVE,
                lastSyncedAt = livePrices.mapNotNull { it.lastSyncedAt }.maxOrNull(),
                i18nEnabled = i18nEnabled
            )
        }

        val providerCached = repository.findByCurrencyIgnoreCaseAndProviderIgnoreCase(currency, provider)
        if (providerCached.isNotEmpty()) {
            val dtos = providerCached.map { it.toDto() }
            return PlanPricesResponse(
                currency = currency,
                prices = dtos,
                provider = provider,
                source = PlanPriceSyncService.SOURCE_PROVIDER_CACHE,
                lastSyncedAt = dtos.mapNotNull { it.lastSyncedAt }.maxOrNull(),
                i18nEnabled = i18nEnabled
            )
        }

        val manual = repository.findByCurrencyIgnoreCaseAndProviderIgnoreCase(currency, "MANUAL")
        if (manual.isNotEmpty()) {
            return PlanPricesResponse(
                currency = currency,
                prices = manual.map { it.toDto() },
                provider = "MANUAL",
                source = "MANUAL",
                i18nEnabled = i18nEnabled
            )
        }

        log.info { "No display prices available for currency=$currency provider=$provider" }
        return PlanPricesResponse(
            currency = currency,
            prices = emptyList(),
            provider = provider,
            source = "UNAVAILABLE",
            i18nEnabled = i18nEnabled
        )
    }

    fun listAll(): List<PlanDisplayPriceDTO> =
        repository.findAllByOrderByCurrencyAscPlanTierAscBillingCycleAsc().map { it.toDto() }

    @Transactional
    fun upsert(request: UpsertPlanDisplayPriceRequest): PlanDisplayPriceDTO {
        val tier = request.planTier.trim().uppercase()
        val cycle = request.billingCycle.trim().uppercase()
        val currency = request.currency.trim().uppercase()
        val provider = request.provider?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "MANUAL"
        validate(tier, cycle, currency, provider, request.amount)

        val existing = repository.findByPlanTierAndBillingCycleAndCurrencyIgnoreCaseAndProviderIgnoreCase(
            tier, cycle, currency, provider
        )
        val saved = if (existing != null) {
            existing.amount = request.amount
            existing.source = "MANUAL"
            repository.save(existing)
        } else {
            repository.save(
                PlanDisplayPrice(
                    planTier = tier,
                    billingCycle = cycle,
                    currency = currency,
                    provider = provider,
                    source = "MANUAL",
                    amount = request.amount,
                    audit = Audit()
                )
            )
        }
        return saved.toDto()
    }

    fun syncProvidersNow(): Map<String, Int> =
        syncService?.syncAllProviderPrices() ?: emptyMap()

    @Transactional
    fun delete(id: Long) {
        if (!repository.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Preço não encontrado")
        }
        repository.deleteById(id)
    }

    fun resolveCurrency(currencyParam: String?, acceptLanguage: String?): String {
        val fromParam = currencyParam?.trim()?.uppercase().orEmpty()
        if (fromParam.isNotBlank()) {
            validateCurrencyCode(fromParam)
            return fromParam
        }
        val locale = LocaleSupport.resolve(acceptLanguage)
        return currencyForLocale(locale)
    }

    private fun currencyForLocale(locale: String): String {
        val normalized = locale.replace('_', '-').lowercase()
        val region = normalized.substringAfter('-', missingDelimiterValue = "")
        when (region) {
            "br" -> return "BRL"
            "us" -> return "USD"
            "gb" -> return "GBP"
            "mx" -> return "MXN"
            "jp" -> return "JPY"
            "kr" -> return "KRW"
            "cn" -> return "CNY"
            "in" -> return "INR"
            "id" -> return "IDR"
        }
        return when {
            normalized.startsWith("pt") -> "BRL"
            normalized.startsWith("en") -> "USD"
            normalized.startsWith("ja") -> "JPY"
            normalized.startsWith("ko") -> "KRW"
            normalized.startsWith("zh") -> "CNY"
            normalized.startsWith("hi") -> "INR"
            normalized.startsWith("id") -> "IDR"
            normalized.startsWith("es") ||
                normalized.startsWith("fr") ||
                normalized.startsWith("de") ||
                normalized.startsWith("it") -> "EUR"
            else -> "USD"
        }
    }

    private fun resolveProvider(providerParam: String?): String {
        val normalized = providerParam?.trim()?.uppercase().orEmpty()
        return when (normalized) {
            PlanPriceSyncService.PROVIDER_GOOGLE_PLAY, "PLAY" -> PlanPriceSyncService.PROVIDER_GOOGLE_PLAY
            PlanPriceSyncService.PROVIDER_STRIPE, "" -> PlanPriceSyncService.PROVIDER_STRIPE
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "provider inválido: $normalized")
        }
    }

    private fun validate(tier: String, cycle: String, currency: String, provider: String, amount: BigDecimal) {
        if (tier !in setOf("FREE", "BASIC", "PLUS", "PREMIUM")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "planTier inválido")
        }
        if (cycle !in setOf("MONTHLY", "ANNUAL")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "billingCycle inválido")
        }
        if (provider !in setOf("MANUAL", PlanPriceSyncService.PROVIDER_STRIPE, PlanPriceSyncService.PROVIDER_GOOGLE_PLAY)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "provider inválido")
        }
        validateCurrencyCode(currency)
        if (amount < BigDecimal.ZERO) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "amount deve ser >= 0")
        }
    }

    private fun validateCurrencyCode(code: String) {
        try {
            Currency.getInstance(code)
        } catch (_: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "currency inválida: $code")
        }
    }

    private fun PlanDisplayPrice.toDto() = PlanDisplayPriceDTO(
        id = id,
        planTier = planTier,
        billingCycle = billingCycle,
        currency = currency,
        amount = amount,
        provider = provider,
        source = source,
        externalPriceId = externalPriceId,
        lastSyncedAt = lastSyncedAt
    )
}
