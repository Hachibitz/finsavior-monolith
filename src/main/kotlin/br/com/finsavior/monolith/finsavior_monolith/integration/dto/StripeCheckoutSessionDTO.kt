package br.com.finsavior.monolith.finsavior_monolith.integration.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class StripeCheckoutSessionDTO(
    @JsonProperty("id")
    val sessionId: String,

    @JsonProperty("customer")
    val customerId: String,

    @JsonProperty("subscription")
    val subscriptionId: String,

    @JsonProperty("customer_details")
    val customerDetails: CustomerDetails,

    @JsonProperty("metadata")
    val metadata: Map<String, String>
)
