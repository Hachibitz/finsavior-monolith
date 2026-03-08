package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import java.math.BigDecimal

data class AiBillExtractionDTO(
    val billName: String? = null,
    val billValue: BigDecimal? = null,
    val billDescription: String? = null,
    val billCategory: String? = null,
    val billTable: BillTableEnum? = null,
    val isInstallment: Boolean? = false,
    val installmentCount: Int? = null,
    val currentInstallment: Int? = null,
    val isRecurrent: Boolean? = false,
    val possibleDate: String? = null,
    val redirectAction: String? = null,
    val cardId: String? = null,
    val targetDate: String? = null,
)
