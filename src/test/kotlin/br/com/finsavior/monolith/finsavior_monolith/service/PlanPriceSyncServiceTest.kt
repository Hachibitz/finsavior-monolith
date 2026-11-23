package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.properties.PlanIdProperties
import br.com.finsavior.monolith.finsavior_monolith.integration.client.StripeClient
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.Recurring
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripeCurrencyOption
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripePrice
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripePriceResponse
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanDisplayPriceRepository
import com.google.api.services.androidpublisher.model.BasePlan
import com.google.api.services.androidpublisher.model.Money
import com.google.api.services.androidpublisher.model.RegionalBasePlanConfig
import com.google.api.services.androidpublisher.model.Subscription
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class PlanPriceSyncServiceTest {
    @Test
    fun `extracts stripe multi-currency options including zero-decimal currencies`() {
        val stripeClient = mock<StripeClient>()
        val playService = mock<GooglePlayBillingService>()
        val repo = mock<PlanDisplayPriceRepository>()
        val props = PlanIdProperties()
        PlanTypeEnum.STRIPE_PLUS_MONTHLY.id = "prod_plus_m"

        val price = StripePrice(
            id = "price_plus_m",
            unit_amount = 1990L,
            currency = "brl",
            recurring = Recurring("month"),
            active = true,
            currency_options = mapOf(
                "usd" to StripeCurrencyOption(399L),
                "jpy" to StripeCurrencyOption(590L)
            )
        )
        whenever(stripeClient.getPricesByProduct(eq("prod_plus_m"), eq(true), any()))
            .thenReturn(StripePriceResponse(listOf(price)))

        val service = PlanPriceSyncService(stripeClient, playService, props, repo)
        val extracted = service.extractStripeCurrencyAmounts("PLUS", "MONTHLY", price).associateBy { it.currency }

        assertEquals(BigDecimal("19.90"), extracted["BRL"]?.amount)
        assertEquals(BigDecimal("3.99"), extracted["USD"]?.amount)
        assertEquals(BigDecimal("590.00"), extracted["JPY"]?.amount)
    }

    @Test
    fun `extracts google play regional base plan prices`() {
        val props = PlanIdProperties()
        val service = PlanPriceSyncService(mock(), mock(), props, mock())

        val brConfig = RegionalBasePlanConfig()
            .setRegionCode("BR")
            .setPrice(Money().setCurrencyCode("BRL").setUnits(19L).setNanos(900_000_000))
        val usConfig = RegionalBasePlanConfig()
            .setRegionCode("US")
            .setPrice(Money().setCurrencyCode("USD").setUnits(3L).setNanos(990_000_000))
        val basePlan = BasePlan()
            .setBasePlanId("monthly")
            .setRegionalConfigs(listOf(brConfig, usConfig))
        val sub = Subscription()
            .setProductId("finsavior_plus_monthly")
            .setBasePlans(listOf(basePlan))

        val extracted = service.extractPlayPricesFromSubscriptions(listOf(sub)).associateBy { it.currency }

        assertEquals(BigDecimal("19.90"), extracted["BRL"]?.amount)
        assertEquals(BigDecimal("3.99"), extracted["USD"]?.amount)
    }
}
