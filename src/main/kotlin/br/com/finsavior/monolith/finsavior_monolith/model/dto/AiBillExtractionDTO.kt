package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.math.BigDecimal

data class AiBillExtractionDTO(
    val billName: String? = null,
    val billValue: BigDecimal? = null,
    val billDescription: String? = null,
    val billCategory: String? = null,
    val isInstallment: Boolean? = false,
    val installmentCount: Int? = null,
    val isRecurrent: Boolean? = false,
    val possibleDate: String? = null
)
