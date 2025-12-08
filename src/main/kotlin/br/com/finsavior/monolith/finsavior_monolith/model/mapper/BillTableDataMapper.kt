package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData

fun BillTableDataDTO.toTableData(): BillTableData =
    BillTableData(
        id = this.id,
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
        entryMethod = this.entryMethod,
        billTable = this.billTable,
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
        entryMethod = this.entryMethod,
        billTable = this.billTable,
    )