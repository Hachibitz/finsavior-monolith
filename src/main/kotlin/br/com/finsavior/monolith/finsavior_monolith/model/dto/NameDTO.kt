package br.com.finsavior.monolith.finsavior_monolith.model.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class NameDTO(
    val fullName: String,
    @JsonProperty("given_name")
    val givenName: String,
    val surName: String
)
