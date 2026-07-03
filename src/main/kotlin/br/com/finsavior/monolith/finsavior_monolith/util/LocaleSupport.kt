package br.com.finsavior.monolith.finsavior_monolith.util

object LocaleSupport {

    fun resolve(acceptLanguage: String?): String {
        if (acceptLanguage.isNullOrBlank()) return DEFAULT
        val primary = acceptLanguage.split(",").firstOrNull()?.trim()?.substringBefore(";") ?: return DEFAULT
        return when {
            primary.equals("pt-BR", true) || primary.equals("pt", true) -> "pt-BR"
            primary.equals("en-US", true) || primary.equals("en", true) -> "en-US"
            primary.equals("es", true) || primary.startsWith("es-", true) -> "es"
            primary.equals("fr", true) || primary.startsWith("fr-", true) -> "fr"
            primary.equals("zh-CN", true) || primary.equals("zh", true) -> "zh-CN"
            primary.equals("ja", true) -> "ja"
            primary.equals("de", true) -> "de"
            primary.equals("it", true) -> "it"
            primary.equals("ko", true) -> "ko"
            primary.equals("hi", true) -> "hi"
            primary.equals("ar", true) -> "ar"
            primary.equals("id", true) -> "id"
            else -> DEFAULT
        }
    }

    fun whisperCode(locale: String): String = when (locale) {
        "pt-BR" -> "pt"
        "en-US" -> "en"
        "zh-CN" -> "zh"
        else -> locale.substringBefore("-").lowercase()
    }

    /** Instruction appended to AI system prompts so responses match the user's UI language. */
    fun aiLanguageInstruction(locale: String): String {
        val languageName = when (locale) {
            "pt-BR" -> "Brazilian Portuguese"
            "en-US" -> "English"
            "es" -> "Spanish"
            "fr" -> "French"
            "zh-CN" -> "Simplified Chinese"
            "ja" -> "Japanese"
            "de" -> "German"
            "it" -> "Italian"
            "ko" -> "Korean"
            "hi" -> "Hindi"
            "ar" -> "Arabic"
            "id" -> "Indonesian"
            else -> "Brazilian Portuguese"
        }
        return """
            |
            |IMPORTANT — User interface language: $languageName ($locale).
            |You MUST write ALL natural-language output (insights, advice, chat replies, explanations) entirely in $languageName.
            |Do not mix languages unless quoting a proper noun from user data.
        """.trimMargin()
    }

    private const val DEFAULT = "pt-BR"
}
