package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class UpdateProfileRequestDTO(
    val firstName: String? = null,
    val lastName: String? = null,
    val username: String? = null
)