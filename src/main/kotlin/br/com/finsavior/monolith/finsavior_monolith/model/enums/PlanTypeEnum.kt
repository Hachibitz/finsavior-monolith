package br.com.finsavior.monolith.finsavior_monolith.model.enums

import java.util.*

enum class PlanTypeEnum(val id: String, val amountOfMonthAnalysisPerMonth: Int, val amountOfTrimesterAnalysisPerMonth: Int, val amountOfAnnualAnalysisPerMonth: Int) {
    FREE("1L", 1, 0, 0),
    PLUS("P-2BA47576SC596340BMZMJJRQ", 12, 3, 1),
    PREMIUM("3L", 12, 3, 1);

    companion object {
        fun fromValue(value: String): PlanTypeEnum {
            return Arrays.stream<PlanTypeEnum>(PlanTypeEnum.entries.toTypedArray())
                .filter { planType: PlanTypeEnum -> planType.id == value }
                .findFirst()
                .orElse(null)
        }
    }
}