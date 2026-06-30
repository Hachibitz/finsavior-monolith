package br.com.finsavior.monolith.finsavior_monolith.config.ai

import br.com.finsavior.monolith.finsavior_monolith.model.enums.AnalysisTypeEnum

/**
 * Central OpenAI model selection for the whole application.
 *
 * GPT-5.4 Nano is currently the cheapest GPT-5.4-class model and is suitable for
 * high-volume classification, extraction and assistant workloads. If we need to
 * change models later, update this file only.
 *
 * Newer OpenAI models reject `max_tokens` and require `max_completion_tokens`
 * instead. LangChain4j exposes that as [maxCompletionTokens] on [OpenAiChatModel].
 */
object OpenAiModelConfig {
    const val DEFAULT_CHAT_MODEL = "gpt-5.4-nano"
    const val TRANSCRIPTION_MODEL = "whisper-1"

    const val CHAT_COMPLETIONS_API_URL = "https://api.openai.com/v1/chat/completions"
    const val AUDIO_TRANSCRIPTIONS_API_URL = "https://api.openai.com/v1/audio/transcriptions"

    /** Savi conversational assistant — keep on Nano for lowest cost; context is trimmed server-side. */
    const val SAVI_CHAT_MODEL = DEFAULT_CHAT_MODEL

    /** Default cap for Savi chat replies (bean used by AiChatService). */
    const val DEFAULT_CHAT_MAX_COMPLETION_TOKENS = 2000

    /** Recent exchanges sent back to the model (each exchange = user + assistant). */
    const val CHAT_HISTORY_EXCHANGES = 6

    const val CHAT_MESSAGE_MAX_CHARS = 1_200
    const val CHAT_QUESTION_MAX_CHARS = 2_000
    const val CHAT_MAX_SEQUENTIAL_TOOL_INVOCATIONS = 3

    /** Short dashboard insight shown on the summary screen. */
    const val QUICK_INSIGHT_MAX_COMPLETION_TOKENS = 100

    /** Goal advice responses are concise markdown tips. */
    const val GOAL_ADVICE_MAX_COMPLETION_TOKENS = 800

    const val MONTHLY_ANALYSIS_MAX_COMPLETION_TOKENS = 2000
    const val TRIMESTER_ANALYSIS_MAX_COMPLETION_TOKENS = 4500
    const val ANNUAL_ANALYSIS_MAX_COMPLETION_TOKENS = 9000

    fun maxCompletionTokensFor(analysisType: AnalysisTypeEnum): Int =
        when (analysisType) {
            AnalysisTypeEnum.TRIMESTER -> TRIMESTER_ANALYSIS_MAX_COMPLETION_TOKENS
            AnalysisTypeEnum.ANNUAL -> ANNUAL_ANALYSIS_MAX_COMPLETION_TOKENS
            else -> MONTHLY_ANALYSIS_MAX_COMPLETION_TOKENS
        }
}
