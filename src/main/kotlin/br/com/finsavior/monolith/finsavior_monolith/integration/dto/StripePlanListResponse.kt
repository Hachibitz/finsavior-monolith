package br.com.finsavior.monolith.finsavior_monolith.integration.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class StripePlanListResponse(
    @JsonProperty("object")
    val content: String,
    val url: String,
    @JsonProperty("has_more")
    val hasMore: Boolean,
    val data: List<StripePlanResponse>
)
