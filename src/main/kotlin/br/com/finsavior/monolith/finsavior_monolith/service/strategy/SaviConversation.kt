package br.com.finsavior.monolith.finsavior_monolith.service.strategy

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.output.Response

interface SaviConversation {
    fun chat(message: String): Response<AiMessage>
}
