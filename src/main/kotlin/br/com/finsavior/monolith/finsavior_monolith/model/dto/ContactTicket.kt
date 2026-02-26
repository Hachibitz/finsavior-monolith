package br.com.finsavior.monolith.finsavior_monolith.model.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class ContactTicket(
    @field:NotBlank
    val name: String,

    @field:Email
    @field:NotBlank
    val email: String,

    @field:Email
    val emailConfirmation: String? = null,

    @field:NotBlank
    val type: String,

    @field:NotBlank
    val message: String,

    // kept for backward compatibility but will be ignored by server-side auth checks
    val isAuthenticated: Boolean = false
)
