package br.com.finsavior.monolith.finsavior_monolith.config.ai

/**
 * Central OpenAI model selection for the whole application.
 *
 * GPT-5.4 Nano is currently the cheapest GPT-5.4-class model and is suitable for
 * high-volume classification, extraction and assistant workloads. If we need to
 * change models later, update this file only.
 */
object OpenAiModelConfig {
    const val DEFAULT_CHAT_MODEL = "gpt-5.4-nano"
    const val TRANSCRIPTION_MODEL = "whisper-1"

    const val CHAT_COMPLETIONS_API_URL = "https://api.openai.com/v1/chat/completions"
    const val AUDIO_TRANSCRIPTIONS_API_URL = "https://api.openai.com/v1/audio/transcriptions"
}
