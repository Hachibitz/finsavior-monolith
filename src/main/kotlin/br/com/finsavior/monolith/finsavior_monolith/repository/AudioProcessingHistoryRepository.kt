package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.AudioProcessingHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface AudioProcessingHistoryRepository : JpaRepository<AudioProcessingHistory, Long> {
    fun countByUserIdAndProcessedAtBetween(userId: Long, start: LocalDateTime, end: LocalDateTime): Int
}