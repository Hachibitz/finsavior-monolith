package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.AiAnalysisHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AnalysisHistoryRepository : JpaRepository<AiAnalysisHistory, Long> {
    fun countByUserIdAndAnalysisTypeIdAndDateBetween(
        userId: Long,
        analysisTypeId: Int,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Int
}