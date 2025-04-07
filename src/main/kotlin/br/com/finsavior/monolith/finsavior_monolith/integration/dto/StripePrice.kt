package br.com.finsavior.monolith.finsavior_monolith.integration.dto

data class StripePrice(
    val id: String,
    val unit_amount: Long?,
    val currency: String,
    val recurring: Recurring?
)
