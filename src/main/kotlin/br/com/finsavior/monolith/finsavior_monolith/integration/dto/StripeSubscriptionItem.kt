package br.com.finsavior.monolith.finsavior_monolith.integration.dto

data class StripeSubscriptionItem(
    val id: String,
    val price: StripePriceDto
)
