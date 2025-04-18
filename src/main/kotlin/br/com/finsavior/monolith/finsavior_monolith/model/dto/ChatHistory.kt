package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class ChatHistory(
    val userMessages: List<String>,
    val assistantMessages: List<String>
)
