package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.AiAdvice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface AiAdviceRepository : JpaRepository<AiAdvice, Long> {
    fun deleteByUserId(userId: Long)

    @Query(
        value = "SELECT COUNT(*) FROM ai_advice WHERE user_id = :userId AND (date >= :initialDate AND date <= :endDate);",
        nativeQuery = true
    )
    fun countAiAdvicesByUserIdAndMonth(userId: Long, initialDate: LocalDateTime, endDate: LocalDateTime): Int?

    @Query(
        value = ("SELECT COUNT(*) " +
                "FROM ai_advice " +
                "WHERE user_id = :userId " +
                "AND (date >= :initialDate " +
                "AND date <= :endDate) " +
                "AND analysis_type_id = :analysisTypeId " +
                "AND del_fg <> 'S';"), nativeQuery = true
    )
    fun countAiAdvicesByUserIdAndMonthAndAnalysisType(
        userId: Long,
        initialDate: LocalDateTime,
        endDate: LocalDateTime,
        analysisTypeId: Int
    ): Int

    fun getTopByUserIdOrderByDateDesc(userId: Long): AiAdvice?

    @Query(
        value = "SELECT TOP(1) * FROM ai_advice WHERE user_id = :userId AND (date >= :initialDate AND date <= :endDate);",
        nativeQuery = true
    )
    fun getAiAdviceByUserIdAndMonth(userId: Long, initialDate: LocalDateTime, endDate: LocalDateTime): AiAdvice?

    fun deleteAllByUserId(userId: Long)

    @Query(value = "SELECT * FROM ai_advice WHERE user_id = :userId AND del_fg <> 'S'", nativeQuery = true)
    fun getAllByUserId(userId: Long): MutableList<AiAdvice?>?
}