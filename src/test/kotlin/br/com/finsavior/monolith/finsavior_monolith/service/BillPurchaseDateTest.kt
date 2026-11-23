package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Installment
import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FixedBillGenerationStrategyEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toBillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAdviceRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.FixedBillRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.InstallmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

class BillPurchaseDateTest {
    @Test
    fun `recurring bills do not invent dates in future months`() {
        val bills = mock<BillTableDataRepository>()
        val fixedBills = mock<FixedBillRepository>()
        val saved = mutableListOf<BillTableData>()
        whenever(fixedBills.save(any<FixedBill>())).thenAnswer { it.getArgument<FixedBill>(0) }
        whenever(bills.save(any<BillTableData>())).thenAnswer {
            it.getArgument<BillTableData>(0).also(saved::add)
        }
        val service = FixedBillService(fixedBills, bills, mock<BillCacheService>())
        val request = BillTableDataDTO(
            userId = 1,
            billType = BillTypeEnum.EXPENSE,
            billDate = "Nov 2026",
            billName = "Rent",
            billValue = BigDecimal("100.00"),
            billDescription = "",
            billTable = BillTableEnum.MAIN,
            paid = false,
            isRecurrent = true,
            purchaseDate = "2026-10-25",
            fixedBillGenerationStrategy = FixedBillGenerationStrategyEnum.YEARLY_UPFRONT
        )

        service.createFixedBill(request, User().copy(id = 1))

        assertEquals(listOf("Nov 2026", "Dec 2026"), saved.map { it.billDate })
        assertEquals(LocalDate.of(2026, 10, 25), saved.first().purchaseDate)
        assertNull(saved.last().purchaseDate)
    }

    @Test
    fun `all installments retain the actual purchase date`() {
        val bills = mock<BillTableDataRepository>()
        val installments = mock<InstallmentRepository>()
        val users = mock<UserService>()
        val user = User().copy(id = 1)
        val saved = mutableListOf<BillTableData>()
        whenever(users.getUserByContext()).thenReturn(user)
        whenever(installments.save(any<Installment>())).thenAnswer { it.getArgument<Installment>(0) }
        whenever(bills.save(any<BillTableData>())).thenAnswer {
            it.getArgument<BillTableData>(0).also(saved::add)
        }
        val service = BillService(bills, users, mock<AiAdviceRepository>(), installments, mock<FixedBillRepository>(), mock<FixedBillService>(), mock<BillCacheService>())
        val request = BillTableDataDTO(
            userId = 1,
            billType = BillTypeEnum.EXPENSE,
            billDate = "Jan 2026",
            billName = "Purchase",
            billValue = BigDecimal("100.00"),
            billDescription = "",
            billTable = BillTableEnum.CREDIT_CARD,
            paid = false,
            isInstallment = true,
            installmentCount = 3,
            currentInstallment = 1,
            purchaseDate = "2025-12-25"
        )

        service.billRegister(request)

        assertEquals(listOf("Jan 2026", "Feb 2026", "Mar 2026"), saved.map { it.billDate })
        assertEquals(List(3) { LocalDate.of(2025, 12, 25) }, saved.map { it.purchaseDate })
        assertEquals(List(3) { "2025-12-25" }, saved.map { it.toBillTableDataDTO().purchaseDate })
    }
}
