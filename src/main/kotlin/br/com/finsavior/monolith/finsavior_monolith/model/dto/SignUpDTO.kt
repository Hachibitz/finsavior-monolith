package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class SignUpDTO (
    val email: String,
    val emailConfirmation: String,
    val username: String,
    val password: String,
    val passwordConfirmation: String,
    val firstName: String,
    val lastName: String,
    val agreement: Boolean,
)