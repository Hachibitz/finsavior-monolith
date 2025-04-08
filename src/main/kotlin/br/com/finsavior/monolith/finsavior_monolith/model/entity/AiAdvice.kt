package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "ai_advice")
data class AiAdvice(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id")
    val userId: Long,
    
    @Column(name = "analysis_type_id")
    val analysisTypeId: Int,

    @Column(name = "prompt", columnDefinition = "LONGTEXT")
    val prompt: String,
    
    @Column(name = "result_message", columnDefinition = "LONGTEXT")
    val resultMessage: String,

    val date: LocalDateTime,
    
    @Column(name = "analysis_start_date")
    val startDate: LocalDateTime,
    
    @Column(name = "analysis_finish_date")
    val finishDate: LocalDateTime,
    
    @Column(name = "temperature")
    val temperature: Float,

    @Column(name = "is_free_analysis")
    val isFreeAnalysis: Boolean? = null, //adicionando essa coluna

    @Embedded
    var audit: Audit? = null
)
