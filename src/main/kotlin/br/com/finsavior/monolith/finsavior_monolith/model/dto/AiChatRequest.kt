package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class AiChatRequest (
    val question: String,
    val chatHistory: List<String>? = null,
    val date: String? = null,
    val isUsingCoins: Boolean? = false,
)