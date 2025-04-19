package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class CheckoutSessionDTO(
    val planType: String? = null,
    val url: String? = null,
    val email: String? = null,
    val clientSecret: String? = null,
)
