package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class ContactTicket(
    val name: String,
    val email: String,
    val emailConfirmation: String? = null,
    val type: String,
    val message: String,
    val isAuthenticated: Boolean = false
)
