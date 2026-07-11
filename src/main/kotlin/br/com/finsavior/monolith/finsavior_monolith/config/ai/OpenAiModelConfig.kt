package br.com.finsavior.monolith.finsavior_monolith.config.ai

import br.com.finsavior.monolith.finsavior_monolith.model.enums.AnalysisTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum

/**
 * Central OpenAI model selection for the whole application.
 *
 * Savi chat is plan-routed:
 * - FREE / BASIC → [SAVI_CHAT_MODEL_BASIC] (gpt-4.1-mini)
 * - PLUS / PREMIUM → [SAVI_CHAT_MODEL_PLUS] (gpt-5.6-luna)
 *
 * High-volume extraction/analysis stays on [DEFAULT_CHAT_MODEL] (nano) for cost control.
 */
object OpenAiModelConfig {
    const val DEFAULT_CHAT_MODEL = "gpt-5.4-nano"
    const val TRANSCRIPTION_MODEL = "whisper-1"

    const val SAVI_CHAT_MODEL_BASIC = "gpt-4.1-mini"
    const val SAVI_CHAT_MODEL_PLUS = "gpt-5.6-luna"

    /** @deprecated Prefer [chatModelForPlan]. Kept for beans that still need a default. */
    const val SAVI_CHAT_MODEL = SAVI_CHAT_MODEL_BASIC

    const val CHAT_COMPLETIONS_API_URL = "https://api.openai.com/v1/chat/completions"
    const val AUDIO_TRANSCRIPTIONS_API_URL = "https://api.openai.com/v1/audio/transcriptions"

    const val DEFAULT_CHAT_MAX_COMPLETION_TOKENS = 2000
    const val CHAT_HISTORY_EXCHANGES = 8
    const val CHAT_MESSAGE_MAX_CHARS = 1_500
    const val CHAT_QUESTION_MAX_CHARS = 2_000
    const val CHAT_MAX_SEQUENTIAL_TOOL_INVOCATIONS = 5

    const val MCP_MAX_BILL_ROWS = 60
    const val MCP_MAX_CHAT_HISTORY_ROWS = 5
    const val MCP_MAX_CHAT_MESSAGE_CHARS = 500

    const val QUICK_INSIGHT_MAX_COMPLETION_TOKENS = 100
    const val GOAL_ADVICE_MAX_COMPLETION_TOKENS = 800
    const val MONTHLY_ANALYSIS_MAX_COMPLETION_TOKENS = 2000
    const val TRIMESTER_ANALYSIS_MAX_COMPLETION_TOKENS = 4500
    const val ANNUAL_ANALYSIS_MAX_COMPLETION_TOKENS = 9000

    fun chatModelForPlan(plan: PlanTypeEnum?): String =
        if (plan?.usesAdvancedChatModel() == true) SAVI_CHAT_MODEL_PLUS else SAVI_CHAT_MODEL_BASIC

    fun maxCompletionTokensFor(analysisType: AnalysisTypeEnum): Int =
        when (analysisType) {
            AnalysisTypeEnum.TRIMESTER -> TRIMESTER_ANALYSIS_MAX_COMPLETION_TOKENS
            AnalysisTypeEnum.ANNUAL -> ANNUAL_ANALYSIS_MAX_COMPLETION_TOKENS
            else -> MONTHLY_ANALYSIS_MAX_COMPLETION_TOKENS
        }
}
