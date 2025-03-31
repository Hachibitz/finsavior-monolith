package br.com.finsavior.monolith.finsavior_monolith.model.enums

enum class AnalysisTypeEnum(val analysisTypeId: Int, val plansCoverageList: List<PlanTypeEnum>) {
    MONTH(1, listOf(PlanTypeEnum.FREE, PlanTypeEnum.PLUS, PlanTypeEnum.PREMIUM)),
    TRIMESTER(2, listOf(PlanTypeEnum.PLUS, PlanTypeEnum.PREMIUM)),
    ANNUAL(3, listOf(PlanTypeEnum.PLUS, PlanTypeEnum.PREMIUM));
}