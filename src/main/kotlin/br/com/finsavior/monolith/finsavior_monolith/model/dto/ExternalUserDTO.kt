package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class ExternalUserDTO(
    val userId: Long,
    val email: String,
    val name: String? = null,
    var planId: String? = null,
    val planStatus: String? = null,
    val subscriptionId: String,
    val externalUserId: String,
)
