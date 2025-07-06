package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity
data class AiAnalysisHistory (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val userId: Long,
    val analysisTypeId: Int,
    val date: LocalDateTime,
    @Column(name = "is_free_analysis")
    val isFreeAnalysis: Boolean? = null,
    @Column(name = "is_using_fscoins")
    val isUsingFsCoins: Boolean = false,

    @Embedded
    var audit: Audit? = null
)