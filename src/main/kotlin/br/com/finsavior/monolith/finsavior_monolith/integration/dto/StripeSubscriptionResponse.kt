package br.com.finsavior.monolith.finsavior_monolith.integration.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class StripeSubscriptionResponse(
    val id: String,
    val items: StripeSubscriptionItemContainer,
    val metadata: Map<String, String>,
    val customer: String,
    @JsonProperty("trial_start")
    val trialStart: Long?,
    @JsonProperty("trial_end")
    val trialEnd: Long?,
)
