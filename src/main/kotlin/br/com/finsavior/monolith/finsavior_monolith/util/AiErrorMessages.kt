package br.com.finsavior.monolith.finsavior_monolith.util

object AiErrorMessages {
    const val CHAT_UNAVAILABLE =
        "Não foi possível falar com a Savi agora. Tente novamente em instantes."
    const val ADVICE_UNAVAILABLE =
        "Não foi possível gerar a análise agora. Tente novamente em instantes."
    const val GOAL_ADVICE_UNAVAILABLE =
        "Não foi possível gerar o conselho agora. Tente novamente em instantes."
    const val QUICK_INSIGHT_FALLBACK =
        "Mantenha o foco nos seus objetivos financeiros!"
    const val GENERIC_UNAVAILABLE =
        "Não foi possível concluir a operação agora. Tente novamente em instantes."

    private val SAFE_USER_MESSAGE_PREFIXES = listOf(
        "Limite de mensagens",
        "Limite de tokens",
        "Pergunta inválida",
        "Análise não encontrada",
        "Tipo de análise não encontrada",
        "Plano não encontrado",
        "Consulta excedida pelo plano",
        "Limite mensal de conselhos",
        "Saldo de FS Coins insuficiente",
        "Usuário não possui FsCoins suficientes",
        "Meta com id",
        "não encontrada"
    )

    private val SENSITIVE_PATTERNS = listOf(
        "openai",
        "gpt-",
        "rate_limit",
        "tokens per min",
        "tpm",
        "organization org-",
        "api.openai",
        "langchain",
        "request too large",
        "\"error\"",
        "exception",
        "stacktrace",
        "jdbc",
        "org.springframework",
        "dev.langchain4j",
        "erro ao se comunicar com o assistente:",
        "falha na comunicação com a api",
        "falha ao gerar quick insight:",
        "falha ao carregar análises:",
        "falha ao deletar análise:"
    )

    fun sanitizeForClient(message: String?, fallback: String = GENERIC_UNAVAILABLE): String {
        if (message.isNullOrBlank()) return fallback
        if (SAFE_USER_MESSAGE_PREFIXES.any { message.startsWith(it, ignoreCase = true) }) {
            return message
        }
        val lower = message.lowercase()
        if (SENSITIVE_PATTERNS.any { lower.contains(it) }) {
            return fallback
        }
        return message
    }
}
