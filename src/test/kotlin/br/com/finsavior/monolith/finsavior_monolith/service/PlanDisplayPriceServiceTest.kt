package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.entity.PlanDisplayPrice
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanDisplayPriceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime

class PlanDisplayPriceServiceTest {
    @Test
    fun `missing currency does not fall back to another currency`() {
        val repository = mock<PlanDisplayPriceRepository>()
        val syncService = mock<PlanPriceSyncService>()
        whenever(syncService.fetchLivePrices("STRIPE", "JPY")).thenReturn(null)
        whenever(repository.findByCurrencyIgnoreCaseAndProviderIgnoreCase("JPY", "STRIPE")).thenReturn(emptyList())
        whenever(repository.findByCurrencyIgnoreCaseAndProviderIgnoreCase("JPY", "MANUAL")).thenReturn(emptyList())

        val result = PlanDisplayPriceService(repository, syncService, i18nEnabled = true, stripeEnabled = true)
            .getPricesForCurrency("JPY", null)

        assertEquals("JPY", result.currency)
        assertEquals("UNAVAILABLE", result.source)
        assertEquals(emptyList<Any>(), result.prices)
        verify(repository, never()).findByCurrencyIgnoreCase("BRL")
    }

    @Test
    fun `when i18n is disabled forces BRL regardless of currency param`() {
        val repository = mock<PlanDisplayPriceRepository>()
        val syncService = mock<PlanPriceSyncService>()
        whenever(syncService.fetchLivePrices("GOOGLE_PLAY", "BRL")).thenReturn(null)
        whenever(repository.findByCurrencyIgnoreCaseAndProviderIgnoreCase("BRL", "GOOGLE_PLAY")).thenReturn(emptyList())
        whenever(repository.findByCurrencyIgnoreCaseAndProviderIgnoreCase("BRL", "MANUAL")).thenReturn(emptyList())

        val result = PlanDisplayPriceService(repository, syncService, i18nEnabled = false, stripeEnabled = false)
            .getPricesForCurrency("USD", "en-US")

        assertEquals("BRL", result.currency)
        assertEquals("GOOGLE_PLAY", result.provider)
        assertEquals(false, result.i18nEnabled)
        assertEquals(false, result.stripeEnabled)
        verify(syncService, never()).fetchLivePrices("GOOGLE_PLAY", "USD")
    }

    @Test
    fun `falls back to provider DB cache when live fetch is unavailable`() {
        val repository = mock<PlanDisplayPriceRepository>()
        val syncService = mock<PlanPriceSyncService>()
        val syncedAt = LocalDateTime.of(2026, 11, 22, 4, 0)
        whenever(syncService.fetchLivePrices("STRIPE", "USD")).thenReturn(null)
        whenever(repository.findByCurrencyIgnoreCaseAndProviderIgnoreCase("USD", "STRIPE")).thenReturn(
            listOf(
                PlanDisplayPrice(
                    id = 1L,
                    planTier = "PLUS",
                    billingCycle = "MONTHLY",
                    currency = "USD",
                    provider = "STRIPE",
                    source = "PROVIDER_CACHE",
                    externalPriceId = "price_123",
                    lastSyncedAt = syncedAt,
                    amount = BigDecimal("3.99")
                )
            )
        )

        val result = PlanDisplayPriceService(repository, syncService, i18nEnabled = true, stripeEnabled = true)
            .getPricesForCurrency("USD", null, "STRIPE")

        assertEquals("USD", result.currency)
        assertEquals("PROVIDER_CACHE", result.source)
        assertEquals(syncedAt, result.lastSyncedAt)
        assertEquals(1, result.prices.size)
        assertEquals(BigDecimal("3.99"), result.prices.first().amount)
    }
}
