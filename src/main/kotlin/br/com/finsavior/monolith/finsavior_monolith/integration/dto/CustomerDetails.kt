package br.com.finsavior.monolith.finsavior_monolith.integration.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CustomerDetails(
    val email: String?,
    val name: String?,
)