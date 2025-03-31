package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class DeleteAccountRequestDTO(
    val username: String,
    val password: String,
    val confirmation: Boolean = false
)
