package br.com.finsavior.monolith.finsavior_monolith.model.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class SubscriberDTO(
    val name: NameDTO,
    @JsonProperty("email_address")
    val email: String,
)
