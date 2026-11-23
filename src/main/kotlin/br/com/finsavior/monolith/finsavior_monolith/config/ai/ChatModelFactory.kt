package br.com.finsavior.monolith.finsavior_monolith.config.ai

import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class ChatModelFactory(
    @param:Value("\${ai.openai.api-key}") private val openAiApiKey: String
) {
    private val cache = ConcurrentHashMap<String, ChatLanguageModel>()

    fun forPlan(plan: PlanTypeEnum?): ChatLanguageModel {
        val modelName = OpenAiModelConfig.chatModelForPlan(plan)
        return cache.computeIfAbsent(modelName) { name ->
            val builder = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(name)
                .maxCompletionTokens(OpenAiModelConfig.DEFAULT_CHAT_MAX_COMPLETION_TOKENS)
                .logRequests(false)
                .logResponses(false)
            // Reasoning models (gpt-5.6-luna) may reject custom temperature when reasoning.effort is not 'none'.
            if (name == OpenAiModelConfig.SAVI_CHAT_MODEL_BASIC) {
                builder.temperature(0.2)
            }
            builder.build()
        }
    }
}
