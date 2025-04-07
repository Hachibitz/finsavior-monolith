package br.com.finsavior.monolith.finsavior_monolith.model.enums

enum class AnalysisTypeEnum(val analysisTypeId: Int, val plansCoverageList: List<PlanTypeEnum>) {
    MONTH(1, PlanTypeEnum.entries),
    TRIMESTER(2, PlanTypeEnum.entries.filter { planTypeEnum ->
        planTypeEnum != PlanTypeEnum.FREE
    }),
    ANNUAL(3, PlanTypeEnum.entries.filter { planTypeEnum ->
        planTypeEnum != PlanTypeEnum.FREE
    });
}