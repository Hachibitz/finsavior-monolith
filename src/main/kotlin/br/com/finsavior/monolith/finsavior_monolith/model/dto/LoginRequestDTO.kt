package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class LoginRequestDTO(
    val username: String,
    val password: String,
    val rememberMe: Boolean,
)