package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class ResetPasswordDTO (
    val newPassword: String,
    val token: String
)