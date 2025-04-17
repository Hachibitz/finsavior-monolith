package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.integration.client.OllamaClient
import br.com.finsavior.monolith.finsavior_monolith.integration.client.OllamaWebClient
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.ChatMessage
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.ChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.GenerateRequest
import kotlinx.coroutines.flow.Flow
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class OllamaService(
    private val client: OllamaClient,
    @Value("\${ollama.model:gemma3}") private val model: String,
    private val webClient: OllamaWebClient
) {

    fun generate(prompt: String, temperature: Float, maxTokens: Int): String {
        val options = mapOf(
            "temperature" to temperature,
            "max_tokens" to maxTokens
        )
        val req = GenerateRequest(model = model, prompt = prompt, options = options)
        val resp = client.generate(req)
        return resp.response
    }

    fun chat(messages: List<ChatMessage>): String {
        val req = ChatRequest(model = model, messages = messages)
        val resp = client.chat(req)
        return resp.message.content
    }

    fun chatStream(messages: List<ChatMessage>): Flow<String> {
        val req = ChatRequest(model = model, messages = messages)
        return webClient.chatStream(req)
    }
}