package br.com.finsavior.monolith.finsavior_monolith.util

import br.com.finsavior.monolith.finsavior_monolith.exception.AiAdviceException
import br.com.finsavior.monolith.finsavior_monolith.exception.ChatbotException
import mu.KotlinLogging

object AiExceptionSupport {
    private val log = KotlinLogging.logger {}

    fun chatCommunicationFailure(cause: Exception): ChatbotException {
        log.error(cause) { "Savi chat communication failed" }
        return ChatbotException(AiErrorMessages.CHAT_UNAVAILABLE, cause)
    }

    fun adviceCommunicationFailure(cause: Exception): AiAdviceException {
        log.error(cause) { "AI advice generation failed" }
        return AiAdviceException(AiErrorMessages.ADVICE_UNAVAILABLE, cause)
    }

    fun goalAdviceCommunicationFailure(cause: Exception): AiAdviceException {
        log.error(cause) { "Goal advice generation failed" }
        return AiAdviceException(AiErrorMessages.GOAL_ADVICE_UNAVAILABLE, cause)
    }

    fun logQuickInsightFailure(userId: Long, date: String?, cause: Exception) {
        log.error(cause) { "Quick insight generation failed for userId=$userId date=$date" }
    }

    fun logAdviceListFailure(cause: Exception) {
        log.error(cause) { "Failed to load AI analyses list" }
    }

    fun logAdviceDeleteFailure(analysisId: Long, cause: Exception) {
        log.error(cause) { "Failed to delete AI analysis id=$analysisId" }
    }
}
