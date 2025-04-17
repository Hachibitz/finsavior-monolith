package br.com.finsavior.monolith.finsavior_monolith.integration.client

import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.ChatRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.reactive.asFlow
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

@Service
class OllamaWebClient {

    private val client = WebClient.builder()
        .baseUrl("http://localhost:11434")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.ACCEPT, "application/x-ndjson")
        .build()

    fun chatStream(request: ChatRequest): Flow<String> {
        return client.post()
            .uri("/api/chat")
            .bodyValue(request)
            .accept(MediaType.parseMediaType("application/x-ndjson"))
            .retrieve()
            .bodyToFlux(String::class.java)
            .asFlow()
    }
}
