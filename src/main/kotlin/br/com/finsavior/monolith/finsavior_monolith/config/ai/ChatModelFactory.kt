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
            OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(name)
                .temperature(0.2)
                .maxCompletionTokens(OpenAiModelConfig.DEFAULT_CHAT_MAX_COMPLETION_TOKENS)
                .logRequests(false)
                .logResponses(false)
                .build()
        }
    }
}
