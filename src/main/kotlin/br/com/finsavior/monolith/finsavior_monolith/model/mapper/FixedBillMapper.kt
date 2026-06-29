package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FixedBillGenerationStrategyEnum
import br.com.finsavior.monolith.finsavior_monolith.util.BillDateUtils
import java.time.LocalDate

private fun parseDayOfMonth(purchaseDate: String?): Int? {
    if (purchaseDate.isNullOrBlank()) return null
    val normalized = purchaseDate.trim().substringBefore('T')
    return runCatching { LocalDate.parse(normalized).dayOfMonth }.getOrNull()
}

fun BillTableDataDTO.toFixedBill(user: User): FixedBill =
    FixedBill(
        user = user,
        billName = this.billName,
        billValue = this.billValue,
        billDescription = this.billDescription,
        billCategory = this.billCategory,
        billType = this.billType,
        billTable = this.billTable,
        paymentType = this.paymentType,
        cardId = this.cardId,
        dayOfMonth = parseDayOfMonth(this.purchaseDate),
        startBillDate = this.billDate,
        generationStrategy = this.fixedBillGenerationStrategy ?: FixedBillGenerationStrategyEnum.YEARLY_UPFRONT,
        active = true,
        audit = Audit()
    )

fun FixedBill.toBillTableData(billDate: String): BillTableData =
    BillTableData(
        id = 0,
        userId = this.user.id!!,
        billType = this.billType,
        billDate = billDate,
        billName = this.billName,
        billValue = this.billValue,
        billDescription = this.billDescription,
        billTable = this.billTable,
        billCategory = this.billCategory,
        isPaid = false,
        isRecurrent = true,
        paymentType = this.paymentType,
        cardId = this.cardId,
        fixedBill = this,
        purchaseDate = BillDateUtils.purchaseDateFor(billDate, this.dayOfMonth),
        audit = Audit()
    )
