package br.com.finsavior.monolith.finsavior_monolith.model.dto

import java.time.LocalDateTime

data class AiAdviceDTO(
    val userId: Long? = null,
    val prompt: String,
    val planId: String,
    val analysisTypeId: Int,
    val mainAndIncomeTable: String,
    val cardTable: String,
    val date: String,
    val temperature: Float,
    val startDate: LocalDateTime,
    val finishDate: LocalDateTime
)
