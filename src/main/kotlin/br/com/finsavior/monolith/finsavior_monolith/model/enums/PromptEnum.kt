package br.com.finsavior.monolith.finsavior_monolith.model.enums

import java.util.*

enum class PromptEnum(promptFileName: String, val analysisType: AnalysisTypeEnum) {
    PROMPT_MONTHLY("prompt-monthly", AnalysisTypeEnum.MONTH),
    PROMPT_TRIMESTER("prompt-trimester", AnalysisTypeEnum.TRIMESTER),
    PROMPT_ANNUAL("prompt-annual", AnalysisTypeEnum.ANNUAL);

    val promptParts: List<String>

    init {
        val properties = Properties()
        val inputStream = this::class.java.classLoader.getResourceAsStream("$promptFileName.properties")
        properties.load(inputStream)
        promptParts = listOf(
            properties.getProperty("$promptFileName-part1"),
            properties.getProperty("$promptFileName-part2"),
            properties.getProperty("$promptFileName-part3"),
            properties.getProperty("$promptFileName-part4")
        )
    }
}