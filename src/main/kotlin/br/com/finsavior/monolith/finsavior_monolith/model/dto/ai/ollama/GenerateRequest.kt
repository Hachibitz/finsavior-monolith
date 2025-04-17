package br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama

data class GenerateRequest(
    val model: String,
    val prompt: String,
    val context: List<Long>? = null,
    val stream: Boolean = false,
    val options: Map<String, Any>? = null
)