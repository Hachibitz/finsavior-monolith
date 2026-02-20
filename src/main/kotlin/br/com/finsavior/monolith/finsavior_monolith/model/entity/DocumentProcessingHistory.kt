package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "document_processing_history")
data class DocumentProcessingHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "processed_at", nullable = false)
    val processedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "document_type", nullable = false)
    val documentType: String,

    @Column(name = "paid_with_coins", nullable = false)
    val paidWithCoins: Boolean = false
)

