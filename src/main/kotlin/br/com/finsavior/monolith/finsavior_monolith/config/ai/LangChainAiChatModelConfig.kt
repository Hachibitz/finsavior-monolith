package br.com.finsavior.monolith.finsavior_monolith.config.ai

import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import dev.langchain4j.model.chat.ChatLanguageModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class LangChainAiChatModelConfig(
    private val chatModelFactory: ChatModelFactory
) {

    /** Default bean used when no plan context is available (falls back to Basic model). */
    @Bean
    fun chatModel(): ChatLanguageModel =
        chatModelFactory.forPlan(PlanTypeEnum.FREE)
}
