package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAnalysisDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.AiAdvice

fun AiAdvice.toAiAnalysisDTO(): AiAnalysisDTO =
    AiAnalysisDTO(
        id = this.id,
        userId = this.userId,
        analysisType = this.analysisTypeId,
        resultAnalysis = this.resultMessage,
        date = this.date,
        startDate = this.startDate,
        finishDate = this.finishDate,
        temperature = this.temperature
    )