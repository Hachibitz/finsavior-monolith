package br.com.finsavior.monolith.finsavior_monolith.integration.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class StripeCheckoutSessionCompletedEvent(
    val id: String,

    @JsonProperty("type")
    val type: String,

    @JsonProperty("data")
    val data: StripeCheckoutSessionDataDTO
)