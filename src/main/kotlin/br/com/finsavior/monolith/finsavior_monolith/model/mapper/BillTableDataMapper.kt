package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import java.time.LocalDate

private fun parsePurchaseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    val normalized = value.trim().substringBefore('T')
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}

fun BillTableDataDTO.toTableData(): BillTableData =
    BillTableData(
        id = this.id ?: 0,
        userId = this.userId,
        billName = this.billName,
        billDescription = this.billDescription,
        billValue = this.billValue,
        billDate = this.billDate,
        billType = this.billType,
        billCategory = this.billCategory,
        isPaid = this.paid,
        totalInstallments = this.installmentCount,
        currentInstallment = this.currentInstallment,
        isInstallment = this.isInstallment,
        isRecurrent = this.isRecurrent,
        entryMethod = this.entryMethod,
        billTable = this.billTable,
        paymentType = this.paymentType,
        cardId = this.cardId,
        purchaseDate = parsePurchaseDate(this.purchaseDate),
    )

fun BillTableData.toBillTableDataDTO(): BillTableDataDTO =
    BillTableDataDTO(
        id = this.id,
        userId = this.userId,
        billName = this.billName,
        billDescription = this.billDescription,
        billValue = this.billValue,
        billDate = this.billDate,
        billType = this.billType,
        billCategory = this.billCategory,
        paid = this.isPaid,
        installmentCount = this.totalInstallments,
        currentInstallment = this.currentInstallment,
        isInstallment = this.isInstallment,
        isRecurrent = this.isRecurrent,
        entryMethod = this.entryMethod,
        billTable = this.billTable,
        paymentType = this.paymentType,
        cardId = this.cardId,
        purchaseDate = this.purchaseDate?.toString(),
        fixedBillGenerationStrategy = this.fixedBill?.generationStrategy,
        fixedBillId = this.fixedBill?.id,
    )
