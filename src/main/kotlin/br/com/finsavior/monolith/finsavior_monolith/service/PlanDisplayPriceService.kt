package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanDisplayPriceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanPricesResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpsertPlanDisplayPriceRequest
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.PlanDisplayPrice
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanDisplayPriceRepository
import br.com.finsavior.monolith.finsavior_monolith.util.LocaleSupport
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.Currency

@Service
class PlanDisplayPriceService(
    private val repository: PlanDisplayPriceRepository
) {
    private val log = KotlinLogging.logger {}

    fun getPricesForCurrency(currencyParam: String?, acceptLanguage: String?): PlanPricesResponse {
        val currency = resolveCurrency(currencyParam, acceptLanguage)
        val prices = repository.findByCurrencyIgnoreCase(currency)
        if (prices.isEmpty()) log.info { "No display prices for $currency" }
        return PlanPricesResponse(currency, prices.map { it.toDto() })
    }

    fun listAll(): List<PlanDisplayPriceDTO> =
        repository.findAllByOrderByCurrencyAscPlanTierAscBillingCycleAsc().map { it.toDto() }

    @Transactional
    fun upsert(request: UpsertPlanDisplayPriceRequest): PlanDisplayPriceDTO {
        val tier = request.planTier.trim().uppercase()
        val cycle = request.billingCycle.trim().uppercase()
        val currency = request.currency.trim().uppercase()
        validate(tier, cycle, currency, request.amount)

        val existing = repository.findByPlanTierAndBillingCycleAndCurrencyIgnoreCase(tier, cycle, currency)
        val saved = if (existing != null) {
            existing.amount = request.amount
            repository.save(existing)
        } else {
            repository.save(
                PlanDisplayPrice(
                    planTier = tier,
                    billingCycle = cycle,
                    currency = currency,
                    amount = request.amount,
                    audit = Audit()
                )
            )
        }
        return saved.toDto()
    }

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

    private fun validate(tier: String, cycle: String, currency: String, amount: BigDecimal) {
        if (tier !in setOf("FREE", "BASIC", "PLUS", "PREMIUM")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "planTier inválido")
        }
        if (cycle !in setOf("MONTHLY", "ANNUAL")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "billingCycle inválido")
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
        amount = amount
    )
}
