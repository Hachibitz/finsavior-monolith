package br.com.finsavior.monolith.finsavior_monolith.model.enums

import br.com.finsavior.monolith.finsavior_monolith.config.properties.PlanIdProperties

enum class PlanTypeEnum(
    var id: String,
    val amountOfMonthAnalysisPerMonth: Int,
    val amountOfTrimesterAnalysisPerMonth: Int,
    val amountOfAnnualAnalysisPerMonth: Int,
    val amountOfChatMessagesWithSavi: Int,
    val maxTokensPerMonth: Int,
    val maxAudioBillEntries: Int,
    val maxGoalAdvicesPerMonth: Int,
    val maxWhatsappMessagesPerMonth: Int
) {
    FREE("1L", 1, 0, 0, 2, 4000, 2, 1, 0),
    STRIPE_BASIC_MONTHLY("UNSET", 3, 1, 0, 15, 30000, Int.MAX_VALUE, 5, 15),
    STRIPE_BASIC_ANNUAL("UNSET", 3, 1, 0, 15, 30000, Int.MAX_VALUE, 5, 15),
    STRIPE_PLUS_MONTHLY("UNSET", 12, 3, 1, 50, 100000, Int.MAX_VALUE, 10, 35),
    STRIPE_PLUS_ANNUAL("UNSET", 12, 3, 1, 50, 100000, Int.MAX_VALUE, 10, 35),
    STRIPE_PREMIUM_MONTHLY("UNSET", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 100),
    STRIPE_PREMIUM_ANNUAL("UNSET", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 100),
    PLAY_BASIC_MONTHLY("UNSET", 3, 1, 0, 15, 30000, Int.MAX_VALUE, 5, 15),
    PLAY_BASIC_ANNUAL("UNSET", 3, 1, 0, 15, 30000, Int.MAX_VALUE, 5, 15),
    PLAY_PLUS_MONTHLY("UNSET", 12, 3, 1, 50, 100000, Int.MAX_VALUE, 10, 35),
    PLAY_PLUS_ANNUAL("UNSET", 12, 3, 1, 50, 100000, Int.MAX_VALUE, 10, 35),
    PLAY_PREMIUM_MONTHLY("UNSET", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 100),
    PLAY_PREMIUM_ANNUAL("UNSET", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, 100);

    companion object {
        fun fromProductId(value: String): PlanTypeEnum? =
            entries.firstOrNull { it.id == value }

        fun fromPlayProductId(value: String): PlanTypeEnum? =
            entries.firstOrNull { it.name.startsWith("PLAY_") && it.id == value }

        fun initialize(planProps: PlanIdProperties) {
            STRIPE_BASIC_MONTHLY.id = planProps.STRIPE_BASIC_MONTHLY
            STRIPE_BASIC_ANNUAL.id = planProps.STRIPE_BASIC_ANNUAL
            STRIPE_PLUS_MONTHLY.id = planProps.STRIPE_PLUS_MONTHLY
            STRIPE_PLUS_ANNUAL.id = planProps.STRIPE_PLUS_ANNUAL
            STRIPE_PREMIUM_MONTHLY.id = planProps.STRIPE_PREMIUM_MONTHLY
            STRIPE_PREMIUM_ANNUAL.id = planProps.STRIPE_PREMIUM_ANNUAL
            PLAY_BASIC_MONTHLY.id = planProps.PLAY_BASIC_MONTHLY
            PLAY_BASIC_ANNUAL.id = planProps.PLAY_BASIC_ANNUAL
            PLAY_PLUS_MONTHLY.id = planProps.PLAY_PLUS_MONTHLY
            PLAY_PLUS_ANNUAL.id = planProps.PLAY_PLUS_ANNUAL
            PLAY_PREMIUM_MONTHLY.id = planProps.PLAY_PREMIUM_MONTHLY
            PLAY_PREMIUM_ANNUAL.id = planProps.PLAY_PREMIUM_ANNUAL
        }
    }
}
