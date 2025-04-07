package br.com.finsavior.monolith.finsavior_monolith.model.enums

import java.util.*

enum class PlanTypeEnum(val id: String, val amountOfMonthAnalysisPerMonth: Int, val amountOfTrimesterAnalysisPerMonth: Int, val amountOfAnnualAnalysisPerMonth: Int) {
    FREE("1L", 1, 0, 0),
    STRIPE_BASIC_MONTHLY("prod_S4glYFmVdBHCgV", 3, 1, 0),
    STRIPE_BASIC_ANNUAL("prod_S4gn06jEDZZ0Sk", 3, 1, 0),
    STRIPE_PLUS_MONTHLY("prod_S4goVk8ymnbNd7", 12, 3, 1),
    STRIPE_PLUS_ANNUAL("prod_S4goyrBbOeFrR2", 12, 3, 1),
    STRIPE_PREMIUM_ANNUAL("prod_S4gsYwP1g3VUYG", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE),
    STRIPE_PREMIUM_MONTHLY("prod_S4gq1Q2s1Et4tm", Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE);

    companion object {
        fun fromProductId(value: String): PlanTypeEnum {
            return Arrays.stream<PlanTypeEnum>(PlanTypeEnum.entries.toTypedArray())
                .filter { planType: PlanTypeEnum -> planType.id == value }
                .findFirst()
                .orElse(null)
        }
    }
}