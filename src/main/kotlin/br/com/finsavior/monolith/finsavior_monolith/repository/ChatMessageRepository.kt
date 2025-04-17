package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageEntity
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ChatMessageRepository : JpaRepository<ChatMessageEntity, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long, pageable: Pageable): List<ChatMessageEntity>
    fun deleteByUserId(userId: Long)
    @Query("""
        SELECT c 
        FROM ChatMessageEntity c 
        WHERE c.userId = :userId 
        ORDER BY c.createdAt DESC
    """)
    fun findRecentMessages(
        @Param("userId") userId: Long,
        pageable: Pageable = PageRequest.of(0, 20)
    ): List<ChatMessageEntity>
}
