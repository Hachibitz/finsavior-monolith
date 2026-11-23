package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
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
    fun `monthly fixed bill is materialized on demand for future months and skipped for earlier months`() {
        val bills = mock<BillTableDataRepository>()
        val fixedBills = mock<FixedBillRepository>()
        val cache = mock<BillCacheService>()
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
        whenever(bills.existsByFixedBillIdAndBillDate(5L, "Feb 2027")).thenReturn(false)
        whenever(bills.save(any<BillTableData>())).thenAnswer { it.getArgument<BillTableData>(0) }

        val service = FixedBillService(fixedBills, bills, cache)

        service.ensureInstancesForUserAndMonth(10L, "Dec 2026")
        verify(bills, never()).save(any<BillTableData>())

        service.ensureInstancesForUserAndMonth(10L, "Feb 2027")
        verify(bills).save(any<BillTableData>())
    }

    @Test
    fun `concurrent requests for the same month materialize only one fixed bill instance`() {
        val bills = mock<BillTableDataRepository>()
        val fixedBills = mock<FixedBillRepository>()
        val cache = mock<BillCacheService>()
        val user = User().copy(id = 10L)

        val template = FixedBill(
            id = 5L,
            user = user,
            billName = "Casa",
            billValue = BigDecimal("1969.00"),
            billType = BillTypeEnum.EXPENSE,
            billTable = BillTableEnum.MAIN,
            startBillDate = "Jan 2027",
            generationStrategy = FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY,
            active = true
        )

        val exists = java.util.concurrent.atomic.AtomicBoolean(false)
        val savedCount = java.util.concurrent.atomic.AtomicInteger(0)
        whenever(fixedBills.findAllByUserIdAndActiveTrue(10L)).thenReturn(listOf(template))
        whenever(bills.existsByFixedBillIdAndBillDate(5L, "Aug 2027")).thenAnswer { exists.get() }
        whenever(bills.save(any<BillTableData>())).thenAnswer {
            Thread.sleep(25)
            exists.set(true)
            savedCount.incrementAndGet()
            it.getArgument<BillTableData>(0)
        }

        val service = FixedBillService(fixedBills, bills, cache)
        val threads = (1..4).map {
            Thread { service.ensureInstancesForUserAndMonth(10L, "Aug 2027") }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals(1, savedCount.get())
    }

    @Test
    fun `deleting single fixed-bill instance excludes that month from future projection`() {
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
        val febInstance = BillTableData(
            id = 99L,
            userId = 10L,
            billType = BillTypeEnum.EXPENSE,
            billDate = "Feb 2027",
            billName = "Aluguel",
            billValue = BigDecimal("1800.00"),
            billTable = BillTableEnum.MAIN,
            billCategory = "Moradia",
            fixedBill = template
        )

        whenever(bills.findById(99L)).thenReturn(Optional.of(febInstance))

        val billService = BillService(
            bills, users, mock<AiAdviceRepository>(), mock<InstallmentRepository>(),
            fixedBills, FixedBillService(fixedBills, bills, cache), cache
        )
        billService.deleteItem(99L, deleteAll = false)

        assertTrue(template.excludedMonths?.contains("Feb 2027") == true)
        verify(fixedBills).save(template)
        verify(bills).delete(febInstance)

        whenever(fixedBills.findAllByUserIdAndActiveTrue(10L)).thenReturn(listOf(template))
        FixedBillService(fixedBills, bills, cache).ensureInstancesForUserAndMonth(10L, "Feb 2027")
        assertEquals("Feb 2027", template.excludedMonths)
        verify(bills, never()).save(any<BillTableData>())
    }
}
