package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FixedBillGenerationStrategyEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAdviceRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.FixedBillRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.InstallmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional

class FixedBillProjectionTest {
    @Test
    fun `projects fixed bill on read for future months without inserting DB rows`() {
        val fixedBills = mock<FixedBillRepository>()
        val user = User().copy(id = 10L)

        val template = FixedBill(
            id = 5L,
            user = user,
            billName = "Aluguel",
            billValue = BigDecimal("1800.00"),
            billType = BillTypeEnum.EXPENSE,
            billTable = BillTableEnum.MAIN,
            startBillDate = "Jan 2027",
            generationStrategy = FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY,
            active = true
        )

        whenever(fixedBills.findAllByUserIdAndActiveTrue(10L)).thenReturn(listOf(template))
        val service = FixedBillService(fixedBills)

        assertTrue(service.projectFixedBillsForMonth(10L, "Dec 2026", BillTableEnum.MAIN, emptyList()).isEmpty())

        val feb = service.projectFixedBillsForMonth(10L, "Feb 2027", BillTableEnum.MAIN, emptyList())
        assertEquals(1, feb.size)
        assertEquals(-5L, feb.single().id)
        assertEquals("Feb 2027", feb.single().billDate)

        // Legacy or already-materialized month row is not duplicated
        val legacyRow = BillTableDataDTO(
            id = 99L,
            userId = 10L,
            billType = BillTypeEnum.EXPENSE,
            billDate = "Feb 2027",
            billName = "Aluguel",
            billValue = BigDecimal("1800.00"),
            billDescription = null,
            billTable = BillTableEnum.MAIN,
            paid = true,
            fixedBillId = 5L
        )
        assertTrue(service.projectFixedBillsForMonth(10L, "Feb 2027", BillTableEnum.MAIN, listOf(legacyRow)).isEmpty())
    }

    @Test
    fun `deleting projected fixed bill for a single month excludes only that month`() {
        val bills = mock<BillTableDataRepository>()
        val fixedBills = mock<FixedBillRepository>()
        val users = mock<UserService>()
        val cache = mock<BillCacheService>()
        val user = User().copy(id = 10L)
        whenever(users.getUserByContext()).thenReturn(user)

        val template = FixedBill(
            id = 5L,
            user = user,
            billName = "Aluguel",
            billValue = BigDecimal("1800.00"),
            billType = BillTypeEnum.EXPENSE,
            billTable = BillTableEnum.MAIN,
            startBillDate = "Jan 2027",
            generationStrategy = FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY,
            active = true
        )

        whenever(fixedBills.findById(5L)).thenReturn(Optional.of(template))
        whenever(fixedBills.findAllByUserIdAndActiveTrue(10L)).thenReturn(listOf(template))

        val fixedService = FixedBillService(fixedBills)
        val billService = BillService(
            bills, users, mock<AiAdviceRepository>(), mock<InstallmentRepository>(),
            fixedBills, fixedService, cache
        )

        billService.deleteItem(-5L, deleteAll = false, billDate = "Feb 2027")

        assertEquals("Feb 2027", template.excludedMonths)
        verify(fixedBills).save(template)
        verify(fixedBills, never()).delete(any<FixedBill>())

        assertTrue(fixedService.projectFixedBillsForMonth(10L, "Feb 2027", BillTableEnum.MAIN, emptyList()).isEmpty())
        assertEquals(1, fixedService.projectFixedBillsForMonth(10L, "Mar 2027", BillTableEnum.MAIN, emptyList()).size)
    }
}
