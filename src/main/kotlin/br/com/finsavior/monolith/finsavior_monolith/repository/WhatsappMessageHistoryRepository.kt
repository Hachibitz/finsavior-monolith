package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.WhatsappMessageHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface WhatsappMessageHistoryRepository : JpaRepository<WhatsappMessageHistory, Long> {
    fun countByUserIdAndCreatedAtBetween(userId: Long, start: LocalDateTime, end: LocalDateTime): Int
    fun countByUserId(userId: Long): Long
}
