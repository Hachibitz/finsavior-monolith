package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class ResetPasswordDTO (
    val newPasswordDTO: String,
    val token: String
)