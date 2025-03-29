package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface BillTableDataRepository : JpaRepository<BillTableData, Long> {
    fun findAllByUserIdAndBillDateAndBillTable(userId: Long, billDate: String, billTable: BillTableEnum): List<BillTableData>
}