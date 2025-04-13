package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessage, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<ChatMessage>
    fun deleteByUserId(userId: Long)
}
