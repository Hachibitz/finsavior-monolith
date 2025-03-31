package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.time.LocalDateTime

data class AiAnalysisDTO(
    var id: Long? = null,
    var userId: Long? = null,
    var analysisType: Int?,
    var resultAnalysis: String?,
    var date: LocalDateTime?,
    var startDate: LocalDateTime?,
    var finishDate: LocalDateTime?,
    var temperature: Float?
)
