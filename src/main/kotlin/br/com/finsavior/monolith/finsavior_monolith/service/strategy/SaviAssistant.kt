package br.com.finsavior.monolith.finsavior_monolith.service.strategy

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.model.output.Response
import dev.langchain4j.service.UserMessage

interface SaviAssistant {

    @UserMessage("{{messages}}")
    fun chat(messages: List<ChatMessage>): Response<AiMessage>
}