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
    fun `recurring bills keep purchase date on template for initial month only`() {
        val fixedBills = mock<FixedBillRepository>()
        whenever(fixedBills.save(any<FixedBill>())).thenAnswer {
            it.getArgument<FixedBill>(0).apply {
                val idField = FixedBill::class.java.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, 77L)
            }
        }
        val service = FixedBillService(fixedBills)
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
            fixedBillGenerationStrategy = FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY
        )

        val returnedId = service.createFixedBill(request, User().copy(id = 1))
        assertEquals(-77L, returnedId)

        val saved = fixedBills.findAllByUserIdAndActiveTrue(1L)
        // Projecting initial month keeps exact purchaseDate; future months do not invent one.
        whenever(fixedBills.findAllByUserIdAndActiveTrue(1L)).thenReturn(
            listOf(
                FixedBill(
                    id = 77L,
                    user = User().copy(id = 1),
                    billName = "Rent",
                    billValue = BigDecimal("100.00"),
                    billType = BillTypeEnum.EXPENSE,
                    billTable = BillTableEnum.MAIN,
                    startBillDate = "Nov 2026",
                    purchaseDate = LocalDate.of(2026, 10, 25),
                    active = true
                )
            )
        )
        val novProjected = service.projectFixedBillsForMonth(1L, "Nov 2026", BillTableEnum.MAIN, emptyList())
        val decProjected = service.projectFixedBillsForMonth(1L, "Dec 2026", BillTableEnum.MAIN, emptyList())

        assertEquals("2026-10-25", novProjected.single().purchaseDate)
        assertNull(decProjected.single().purchaseDate)
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
