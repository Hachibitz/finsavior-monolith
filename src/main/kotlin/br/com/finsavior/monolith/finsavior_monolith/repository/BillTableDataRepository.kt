package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillEntryMethodEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface BillTableDataRepository : JpaRepository<BillTableData, Long> {
    fun findAllByUserIdAndBillDateAndBillTable(userId: Long, billDate: String, billTable: BillTableEnum): List<BillTableData>
    fun deleteByUserId(userId: Long)

    @Query("SELECT COUNT(b) FROM BillTableData b WHERE b.userId = :userId AND b.entryMethod = :entryMethod AND b.audit.insertDtm BETWEEN :startDate AND :endDate")
    fun countByUserIdAndEntryMethodAndDateRange(
        @Param("userId") userId: Long,
        @Param("entryMethod") entryMethod: BillEntryMethodEnum,
        @Param("startDate") startDate: LocalDateTime,
        @Param("endDate") endDate: LocalDateTime
    ): Int
}