package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.DocumentProcessingHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface DocumentProcessingHistoryRepository : JpaRepository<DocumentProcessingHistory, Long> {
    fun countByUserIdAndProcessedAtBetweenAndPaidWithCoinsFalse(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Int

    fun countByUserIdAndProcessedAtBetween(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Int
}

