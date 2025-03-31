package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class ChangePasswordRequestDTO(
    val username: String,
    val currentPassword: String,
    val newPassword: String
)
