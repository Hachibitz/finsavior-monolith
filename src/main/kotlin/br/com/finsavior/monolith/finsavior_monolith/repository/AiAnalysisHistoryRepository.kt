package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.AiAnalysisHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AiAnalysisHistoryRepository : JpaRepository<AiAnalysisHistory, Long> {
    fun countByUserIdAndAnalysisTypeIdAndDateBetweenAndIsUsingFsCoinsFalse(
        userId: Long,
        analysisTypeId: Int,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Int

    fun existsByUserIdAndIsFreeAnalysisTrue(userId: Long): Boolean
}