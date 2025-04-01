package br.com.finsavior.monolith.finsavior_monolith.model.enums

import br.com.finsavior.monolith.finsavior_monolith.config.PromptConfig

enum class PromptEnum(val analysisType: AnalysisTypeEnum) {
    PROMPT_MONTHLY(AnalysisTypeEnum.MONTH) {
        override fun getPromptParts(config: PromptConfig): List<String> {
            return listOf(
                config.promptMonthlyPart1,
                config.promptMonthlyPart2,
                config.promptMonthlyPart3,
                config.promptMonthlyPart4
            )
        }
    },
    PROMPT_TRIMESTER(AnalysisTypeEnum.TRIMESTER) {
        override fun getPromptParts(config: PromptConfig): List<String> {
            return listOf(
                config.promptTrimesterPart1,
                config.promptTrimesterPart2,
                config.promptTrimesterPart3,
                config.promptTrimesterPart4
            )
        }
    },
    PROMPT_ANNUAL(AnalysisTypeEnum.ANNUAL) {
        override fun getPromptParts(config: PromptConfig): List<String> {
            return listOf(
                config.promptAnnualPart1,
                config.promptAnnualPart2,
                config.promptAnnualPart3,
                config.promptAnnualPart4
            )
        }
    };

    abstract fun getPromptParts(config: PromptConfig): List<String>
}