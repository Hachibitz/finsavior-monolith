package br.com.finsavior.monolith.finsavior_monolith.integration.dto

data class StripeCurrencyOption(
    val unit_amount: Long? = null
)

data class StripePrice(
    val id: String,
    val unit_amount: Long?,
    val currency: String,
    val recurring: Recurring?,
    val active: Boolean? = true,
    val currency_options: Map<String, StripeCurrencyOption>? = null
)
