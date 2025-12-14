package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillEntryMethodEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import java.math.BigDecimal

data class BillTableDataDTO (
    var id: Long,
    var userId: Long,
    var billType: String,
    var billDate: String,
    var billName: String,
    var billValue: BigDecimal,
    var billDescription: String?,
    var billTable: BillTableEnum,
    var billCategory: String? = null,
    var paid: Boolean,
    var isInstallment: Boolean? = false,
    var installmentCount: Int? = null,
    var currentInstallment: Int? = null,
    var entryMethod: BillEntryMethodEnum = BillEntryMethodEnum.MANUAL,
    var isRecurrent: Boolean? = null,
    var paymentType: String? = null,
)