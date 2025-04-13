package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "chat_message_history")
data class ChatMessageHistory(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id")
    val userId: Long,

    @Column(name = "chat_message_id")
    val chatMessageId: Long,

    @Column(name = "tokens_used")
    val tokensUsed: Long,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now()
)
