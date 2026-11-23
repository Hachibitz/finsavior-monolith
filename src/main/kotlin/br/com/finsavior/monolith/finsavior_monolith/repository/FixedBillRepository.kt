package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface FixedBillRepository : JpaRepository<FixedBill, Long> {
    fun findAllByActiveTrue(): List<FixedBill>
    fun deleteByUserId(userId: Long)
}
