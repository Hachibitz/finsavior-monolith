package br.com.finsavior.monolith.finsavior_monolith.config.ai

import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI
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
            .modelName(GPT_4_O_MINI)
            .temperature(0.2)
            .maxTokens(2000)
            .logRequests(true)
            .logResponses(true)
            .build()
}