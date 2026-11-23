package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.AudioProcessingHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AudioProcessingHistoryRepository : JpaRepository<AudioProcessingHistory, Long> {
    fun countByUserIdAndProcessedAtBetween(userId: Long, start: LocalDateTime, end: LocalDateTime): Int

    fun countByUserIdAndProcessedAtBetweenAndPaidWithCoinsFalseAndStatusNot(
        userId: Long,
        start: LocalDateTime,
        end: LocalDateTime,
        status: br.com.finsavior.monolith.finsavior_monolith.model.enums.AudioProcessingStatus
    ): Int

    @Query(
        value = """
            SELECT COALESCE(SUM(duration_seconds), 0)
            FROM audio_processing_history
            WHERE user_id = :userId
              AND paid_with_coins = false
              AND status <> 'ERROR'
              AND processed_at BETWEEN :start AND :end
            """,
        nativeQuery = true
    )
    fun sumDurationSecondsFreeInPeriod(
        @Param("userId") userId: Long,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime
    ): Long
}
