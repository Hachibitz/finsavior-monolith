package br.com.finsavior.monolith.finsavior_monolith.integration.client

import br.com.finsavior.monolith.finsavior_monolith.integration.client.config.OllamaFeignConfig
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.ChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.GenerateRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.GenerateResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.NonStreamChatResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "ollamaClient",
    url = "\${ollama.url:http://localhost:11434}",
    configuration = [OllamaFeignConfig::class]
)
interface OllamaClient {

    @PostMapping("/api/generate")
    fun generate(@RequestBody req: GenerateRequest): GenerateResponse

    @PostMapping("/api/chat")
    fun chat(@RequestBody req: ChatRequest): NonStreamChatResponse

}