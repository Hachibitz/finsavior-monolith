package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.math.BigDecimal

data class PurchaseAmountDTO(
    val currencyCode: String,
    val value: BigDecimal
)
