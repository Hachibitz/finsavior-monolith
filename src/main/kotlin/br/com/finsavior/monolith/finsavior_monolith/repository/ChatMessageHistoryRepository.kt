package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageHistory
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface ChatMessageHistoryRepository : JpaRepository<ChatMessageHistory, Long> {
    fun countByUserIdAndCreatedAtBetween(userId: Long, start: LocalDateTime, end: LocalDateTime): Int
    fun findAllByUserIdAndCreatedAtBetween(userId: Long, start: LocalDateTime, end: LocalDateTime): List<ChatMessageHistory>
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<ChatMessageHistory>
    fun deleteByUserId(userId: Long)
}
