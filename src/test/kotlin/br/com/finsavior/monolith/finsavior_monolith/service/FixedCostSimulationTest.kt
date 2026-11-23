package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.ai.MCPToolsConfig
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTypeEnum
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal

class FixedCostSimulationTest {
    @Test
    fun `calculates complete amounts and rejects exclusions outside fixed costs`() {
        val bills = mock<BillService>()
        val service = MCPToolsConfig(mock(), mock(), mock(), bills, mock(), mock(), mock())
        val main = (1L..61L).map { bill(it, BillTableEnum.MAIN, recurrent = true) }
        val card = listOf(bill(62, BillTableEnum.CREDIT_CARD, installment = true))
        whenever(bills.loadMainTableData("Dec 2026")).thenReturn(main)
        whenever(bills.loadCardTableData("Dec 2026")).thenReturn(card)

        val result = service.simulateFixedCosts("Dec 2026", listOf(62), BigDecimal("200"))
        assertEquals(BigDecimal("620.00"), result.fixedCosts)
        assertEquals(BigDecimal("610.00"), result.simulatedCosts)
        assertEquals(BigDecimal("305.00"), result.percentage)
        assertEquals(60, result.includedItems.size)
        assertEquals(true, result.itemsTruncated)
        assertThrows(IllegalArgumentException::class.java) {
            service.simulateFixedCosts("Dec 2026", listOf(999), BigDecimal("200"))
        }
    }

    private fun bill(id: Long, table: BillTableEnum, recurrent: Boolean = false, installment: Boolean = false) =
        BillTableDataDTO(
            id = id, userId = 1, billType = BillTypeEnum.EXPENSE, billDate = "Dec 2026",
            billName = "Bill $id", billValue = BigDecimal("10.00"), billDescription = null,
            billTable = table, paid = false, isRecurrent = recurrent, isInstallment = installment
        )
}
