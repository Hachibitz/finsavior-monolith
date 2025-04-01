package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class PurchaseUnitDTO(
    val referenceId: String,
    val amount: PurchaseAmountDTO,
    val payee: PayeeDTO,
    val shipping: ShippingDTO
)
