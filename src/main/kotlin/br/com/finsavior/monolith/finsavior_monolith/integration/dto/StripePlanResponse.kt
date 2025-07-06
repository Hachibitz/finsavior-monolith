package br.com.finsavior.monolith.finsavior_monolith.integration.dto

data class StripePlanResponse(
    val id: String,
    val active: Boolean,
    val amount: Long,
    val currency: String,
    val interval: String,
    val product: String,
    val nickname: String? = null,
)