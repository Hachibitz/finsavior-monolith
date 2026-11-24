package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpdateFixedBillRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpdateInstallmentRequest
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Installment
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FixedBillGenerationStrategyEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.FixedBillRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.InstallmentRepository
import br.com.finsavior.monolith.finsavior_monolith.util.BillDateUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional

class FixedBillManagementTest {

    @Test
    fun `migrating fixed bill from monthly to yearly upfront materializes remaining months`() {
        val fixedBills = mock<FixedBillRepository>()
        val bills = mock<BillTableDataRepository>()
        val cache = mock<BillCacheService>()
        val userService = mock<UserService>()

        val user = User().copy(id = 1L)
        val currentMonth = BillDateUtils.currentMonthYear().toBillDate()
        val fixedBill = FixedBill(
            id = 2L,
            user = user,
            billName = "Thai Gym - Felipe",
            billValue = BigDecimal("185.00"),
            billCategory = "personal_care",
            billType = BillTypeEnum.EXPENSE,
            billTable = BillTableEnum.CREDIT_CARD,
            cardId = "71",
            dayOfMonth = 18,
            startBillDate = "Sep 2026",
            generationStrategy = FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY,
            active = true
        )

        val savedInstances = mutableListOf<BillTableData>()
        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(fixedBills.findById(2L)).thenReturn(Optional.of(fixedBill))
        whenever(fixedBills.save(any<FixedBill>())).thenAnswer { it.getArgument<FixedBill>(0) }
        whenever(bills.findAllByFixedBillId(2L)).thenReturn(
            listOf(
                BillTableData(
                    id = 10L,
                    userId = 1L,
                    billType = BillTypeEnum.EXPENSE,
                    billDate = currentMonth,
                    billName = "Thai Gym - Felipe",
                    billValue = BigDecimal("185.00"),
                    billTable = BillTableEnum.CREDIT_CARD,
                    billCategory = "personal_care",
                    isPaid = false,
                    isRecurrent = true,
                    fixedBill = fixedBill
                )
            )
        )
        whenever(bills.save(any<BillTableData>())).thenAnswer {
            it.getArgument<BillTableData>(0).also(savedInstances::add)
        }

        val service = FixedBillService(fixedBills, bills, cache, userService)
        val result = service.migrateStrategy(2L, FixedBillGenerationStrategyEnum.YEARLY_UPFRONT)

        assertEquals(FixedBillGenerationStrategyEnum.YEARLY_UPFRONT, result.generationStrategy)
        val expectedMissingCount = BillDateUtils.monthsThroughDecember(BillDateUtils.currentMonthYear()).size - 1
        assertEquals(expectedMissingCount, savedInstances.size)
        verify(cache, atLeastOnce()).evictUserCaches(1L)
    }

    @Test
    fun `updating fixed bill updates future open instances and preserves paid ones`() {
        val fixedBills = mock<FixedBillRepository>()
        val bills = mock<BillTableDataRepository>()
        val cache = mock<BillCacheService>()
        val userService = mock<UserService>()

        val user = User().copy(id = 1L)
        val currentMonth = BillDateUtils.currentMonthYear().toBillDate()
        val fixedBill = FixedBill(
            id = 2L,
            user = user,
            billName = "Thai Gym - Felipe",
            billValue = BigDecimal("185.00"),
            billCategory = "personal_care",
            billType = BillTypeEnum.EXPENSE,
            billTable = BillTableEnum.CREDIT_CARD,
            cardId = "71",
            startBillDate = "Sep 2026",
            generationStrategy = FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY,
            active = true
        )

        val paidPast = BillTableData(
            id = 10L, userId = 1L, billType = BillTypeEnum.EXPENSE, billDate = "Sep 2026",
            billName = "Thai Gym - Felipe", billValue = BigDecimal("185.00"),
            billTable = BillTableEnum.CREDIT_CARD, billCategory = "personal_care",
            isPaid = true, isRecurrent = true, fixedBill = fixedBill
        )
        val openCurrent = BillTableData(
            id = 11L, userId = 1L, billType = BillTypeEnum.EXPENSE, billDate = currentMonth,
            billName = "Thai Gym - Felipe", billValue = BigDecimal("185.00"),
            billTable = BillTableEnum.CREDIT_CARD, billCategory = "personal_care",
            isPaid = false, isRecurrent = true, fixedBill = fixedBill
        )

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(fixedBills.findById(2L)).thenReturn(Optional.of(fixedBill))
        whenever(fixedBills.save(any<FixedBill>())).thenAnswer { it.getArgument<FixedBill>(0) }
        whenever(bills.findAllByFixedBillId(2L)).thenReturn(listOf(paidPast, openCurrent))
        whenever(bills.save(any<BillTableData>())).thenAnswer { it.getArgument<BillTableData>(0) }

        val service = FixedBillService(fixedBills, bills, cache, userService)
        service.updateFixedBill(
            2L,
            UpdateFixedBillRequest(
                billName = "Thai Gym Plano Anual",
                billValue = BigDecimal("160.00"),
                billCategory = "personal_care",
                applyToFutureOpenBills = true
            )
        )

        assertEquals("Thai Gym - Felipe", paidPast.billName)
        assertEquals(BigDecimal("185.00"), paidPast.billValue)
        assertEquals("Thai Gym Plano Anual", openCurrent.billName)
        assertEquals(BigDecimal("160.00"), openCurrent.billValue)
    }

    @Test
    fun `deleting fixed bill future only unlinks paid history`() {
        val fixedBills = mock<FixedBillRepository>()
        val bills = mock<BillTableDataRepository>()
        val cache = mock<BillCacheService>()
        val userService = mock<UserService>()

        val user = User().copy(id = 1L)
        val currentMonth = BillDateUtils.currentMonthYear().toBillDate()
        val fixedBill = FixedBill(
            id = 2L, user = user, billName = "Thai Gym", billValue = BigDecimal("185.00"),
            billType = BillTypeEnum.EXPENSE, billTable = BillTableEnum.CREDIT_CARD,
            startBillDate = "Sep 2026"
        )
        val paidPast = BillTableData(
            id = 10L, userId = 1L, billType = BillTypeEnum.EXPENSE, billDate = "Sep 2026",
            billName = "Thai Gym", billValue = BigDecimal("185.00"),
            billTable = BillTableEnum.CREDIT_CARD, billCategory = "personal_care",
            isPaid = true, isRecurrent = true, fixedBill = fixedBill
        )
        val openCurrent = BillTableData(
            id = 11L, userId = 1L, billType = BillTypeEnum.EXPENSE, billDate = currentMonth,
            billName = "Thai Gym", billValue = BigDecimal("185.00"),
            billTable = BillTableEnum.CREDIT_CARD, billCategory = "personal_care",
            isPaid = false, isRecurrent = true, fixedBill = fixedBill
        )

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(fixedBills.findById(2L)).thenReturn(Optional.of(fixedBill))
        whenever(bills.findAllByFixedBillId(2L)).thenReturn(listOf(paidPast, openCurrent))

        val service = FixedBillService(fixedBills, bills, cache, userService)
        service.deleteFixedBill(2L, deleteFutureOnly = true)

        assertNull(paidPast.fixedBill)
        verify(bills).deleteAll(listOf(openCurrent))
        verify(fixedBills).delete(fixedBill)
    }

    @Test
    fun `installment service updates unpaid installments only`() {
        val installments = mock<InstallmentRepository>()
        val bills = mock<BillTableDataRepository>()
        val cache = mock<BillCacheService>()
        val userService = mock<UserService>()

        val user = User().copy(id = 1L)
        val inst = Installment(id = 5L, user = user)
        val b1 = BillTableData(
            id = 1L, userId = 1L, billType = BillTypeEnum.EXPENSE, billDate = "Oct 2026",
            billName = "Notebook", billValue = BigDecimal("500.00"),
            billTable = BillTableEnum.CREDIT_CARD, billCategory = "electronics",
            isPaid = true, isInstallment = true, currentInstallment = 1, totalInstallments = 2,
            installment = inst
        )
        val b2 = BillTableData(
            id = 2L, userId = 1L, billType = BillTypeEnum.EXPENSE, billDate = "Nov 2026",
            billName = "Notebook", billValue = BigDecimal("500.00"),
            billTable = BillTableEnum.CREDIT_CARD, billCategory = "electronics",
            isPaid = false, isInstallment = true, currentInstallment = 2, totalInstallments = 2,
            installment = inst
        )
        inst.bills.addAll(listOf(b1, b2))

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(installments.findById(5L)).thenReturn(Optional.of(inst))
        whenever(bills.save(any<BillTableData>())).thenAnswer { it.getArgument<BillTableData>(0) }

        val service = InstallmentService(installments, bills, cache, userService)
        val updated = service.updateInstallment(
            5L,
            UpdateInstallmentRequest(
                billName = "MacBook",
                billValue = BigDecimal("550.00"),
                billCategory = "electronics",
                applyToUnpaidOnly = true
            )
        )

        assertEquals("Notebook", b1.billName)
        assertEquals("MacBook", b2.billName)
        assertEquals(BigDecimal("550.00"), b2.billValue)
        assertEquals(1, updated.remainingCount)
    }
}
