package br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama

data class ChatRequest(
    val model: String,
    val stream: Boolean = false,
    val messages: List<ChatMessage>
)