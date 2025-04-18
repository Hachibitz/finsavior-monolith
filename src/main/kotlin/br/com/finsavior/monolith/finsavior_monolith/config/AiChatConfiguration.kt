package br.com.finsavior.monolith.finsavior_monolith.config

import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AiChatConfiguration {

    @Bean
    fun defaultChatOptions(
        @Value("\${spring.ai.openai.chat.options.model:gpt-4o-mini}") model: String,
        @Value("\${spring.ai.openai.chat.options.temperature:0.2}") temperature: Double,
        @Value("\${spring.ai.openai.chat.options.max-tokens:1000}") maxTokens: Int
    ): OpenAiChatOptions {
        return OpenAiChatOptions.builder()
            .withModel(model)
            .withTemperature(temperature.toFloat())
            .withMaxTokens(maxTokens)
            .build()
    }
}