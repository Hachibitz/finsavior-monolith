package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.time.LocalDateTime

data class AiAdviceDTO(
    val userId: Long? = null,
    var prompt: String? = null,
    val planId: String? = null,
    val analysisTypeId: Int,
    val temperature: Float,
    val startDate: LocalDateTime,
    val finishDate: LocalDateTime
)
