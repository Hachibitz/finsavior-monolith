package br.com.finsavior.monolith.finsavior_monolith.model.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ResourceDTO(
    val id: String,
    val quantity: String,
    val subscriber: SubscriberDTO,
    @JsonProperty("create_time")
    val createTime: String,
    @JsonProperty("plan_id")
    val planId: String,
)
