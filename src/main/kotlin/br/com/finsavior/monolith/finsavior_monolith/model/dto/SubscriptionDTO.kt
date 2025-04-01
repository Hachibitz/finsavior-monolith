package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.time.LocalDateTime

data class SubscriptionDTO(
    val planId: String? = null,
    val externalUserId: String,
    val subscriptionId: String,
    val intent: String? = null,
    val status: String? = null,
    val purchaseUnits: ArrayList<PurchaseUnitDTO>? = null,
    val payer: PayerDTO,
    val createTime: LocalDateTime? = null,
)
