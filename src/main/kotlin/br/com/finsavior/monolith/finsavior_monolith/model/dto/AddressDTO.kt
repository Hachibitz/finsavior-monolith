package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class AddressDTO(
    val addressLine1: String,
    val addressLine2: String,
    val adminArea2: String,
    val adminArea1: String,
    val postalCode: String,
    val countryCode: String
)
