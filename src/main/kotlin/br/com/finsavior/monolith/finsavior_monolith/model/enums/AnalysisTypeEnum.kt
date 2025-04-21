package br.com.finsavior.monolith.finsavior_monolith.model.enums

enum class AnalysisTypeEnum(val analysisTypeId: Int, val plansCoverageList: List<PlanTypeEnum>, val period: Int) {
    MONTH(1, PlanTypeEnum.entries, 1),
    TRIMESTER(2, PlanTypeEnum.entries.filter { planTypeEnum ->
        planTypeEnum != PlanTypeEnum.FREE
    }, 3),
    ANNUAL(3, PlanTypeEnum.entries.filter { planTypeEnum ->
        planTypeEnum != PlanTypeEnum.FREE
    }, 12);
}