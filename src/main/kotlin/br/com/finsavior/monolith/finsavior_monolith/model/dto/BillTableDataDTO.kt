package br.com.finsavior.monolith.finsavior_monolith.model.dto

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
    var paid: Boolean,
    var isRecurrent: Boolean? = null,
)