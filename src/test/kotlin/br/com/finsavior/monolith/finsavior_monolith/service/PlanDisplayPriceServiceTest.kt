package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.repository.PlanDisplayPriceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PlanDisplayPriceServiceTest {
    @Test
    fun `missing currency does not fall back to another currency`() {
        val repository = mock<PlanDisplayPriceRepository>()
        whenever(repository.findByCurrencyIgnoreCase("JPY")).thenReturn(emptyList())

        val result = PlanDisplayPriceService(repository).getPricesForCurrency("JPY", null)

        assertEquals("JPY", result.currency)
        assertEquals(emptyList<Any>(), result.prices)
        verify(repository, never()).findByCurrencyIgnoreCase("BRL")
    }
}
