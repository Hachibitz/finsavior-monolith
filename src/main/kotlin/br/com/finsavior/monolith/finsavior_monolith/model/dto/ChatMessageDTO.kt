package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.time.LocalDateTime

data class ChatMessageDTO(
    val userMessage: String,
    val assistantResponse: String,
    val createdAt: LocalDateTime
)