package br.com.finsavior.monolith.finsavior_monolith.integration.dto

data class StripeSubscriptionResponse(
    val items: StripeSubscriptionItemContainer,
    val metadata: Map<String, String>,
    val customer: String
)
