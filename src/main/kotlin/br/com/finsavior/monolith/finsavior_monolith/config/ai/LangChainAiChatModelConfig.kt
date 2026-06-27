package br.com.finsavior.monolith.finsavior_monolith.config.ai

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LangChainAiChatModelConfig(
    @param:Value("\${ai.openai.api-key}") private val openAiApiKey: String
) {

    @Bean
    fun chatModel(): ChatLanguageModel =
        OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(OpenAiModelConfig.DEFAULT_CHAT_MODEL)
            .temperature(0.2)
            .logRequests(false)
            .logResponses(false)
            .build()
}