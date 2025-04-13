package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ChatMessageDTO
import br.com.finsavior.monolith.finsavior_monolith.service.AiChatService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ai")
class AiAssistantController(
    private val aiChatService: AiChatService
) {

    @PostMapping("/chat")
    fun chatWithAssistant(
        @RequestBody request: AiChatRequest
    ): ResponseEntity<AiChatResponse> =
        aiChatService.chatWithAssistant(request)

    @GetMapping("/history")
    fun getChatHistory(
        @RequestParam offset: Int,
        @RequestParam limit: Int
    ): ResponseEntity<List<ChatMessageDTO>> =
        aiChatService.getUserChatHistory(offset, limit)

    @DeleteMapping("/delete-chat-history")
    fun clearChatHistory(): ResponseEntity<Void> =
        aiChatService.clearUserChatHistory()

}
