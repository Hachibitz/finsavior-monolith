package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.UnauthorizedException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.InstallmentGroupDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpdateInstallmentRequest
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Installment
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CommonEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toBillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.InstallmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class InstallmentService(
    private val installmentRepository: InstallmentRepository,
    private val billTableDataRepository: BillTableDataRepository,
    private val billCacheService: BillCacheService,
    private val userService: UserService
) {

    @Transactional(readOnly = true)
    fun listInstallments(): List<InstallmentGroupDTO> {
        val user = userService.getUserByContext()
        return installmentRepository.findAllByUserId(user.id!!)
            .filter { it.bills.isNotEmpty() }
            .sortedByDescending { it.id }
            .map { toGroupDTO(it) }
    }

    @Transactional
    fun updateInstallment(id: Long, request: UpdateInstallmentRequest): InstallmentGroupDTO {
        val user = userService.getUserByContext()
        val installment = getOwnedInstallment(id, user.id!!)

        val trimmedName = request.billName.trim().take(100)
        installment.bills
            .filter { !request.applyToUnpaidOnly || !it.isPaid }
            .forEach { bill ->
                bill.apply {
                    billName = trimmedName
                    billValue = request.billValue
                    billCategory = request.billCategory
                    request.billTable?.let { billTable = it }
                    cardId = request.cardId
                    audit?.updateDtm = LocalDateTime.now()
                    audit?.updateId = CommonEnum.APP_ID.name
                }
                billTableDataRepository.save(bill)
            }

        billCacheService.evictUserCaches(user.id!!)
        return toGroupDTO(installment)
    }

    @Transactional
    fun deleteInstallment(id: Long, deleteUnpaidOnly: Boolean) {
        val user = userService.getUserByContext()
        val installment = getOwnedInstallment(id, user.id!!)

        if (deleteUnpaidOnly) {
            val unpaidBills = installment.bills.filter { !it.isPaid }
            if (unpaidBills.size == installment.bills.size) {
                installmentRepository.delete(installment)
            } else {
                installment.bills.removeAll(unpaidBills.toSet())
                billTableDataRepository.deleteAll(unpaidBills)
                installmentRepository.save(installment)
            }
        } else {
            installmentRepository.delete(installment)
        }

        billCacheService.evictUserCaches(user.id!!)
    }

    private fun getOwnedInstallment(id: Long, userId: Long): Installment {
        val installment = installmentRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Parcelamento não encontrado") }
        if (installment.user.id != userId) {
            throw UnauthorizedException("Você não tem permissão para acessar este parcelamento")
        }
        return installment
    }

    private fun toGroupDTO(installment: Installment): InstallmentGroupDTO {
        val sortedBills = installment.bills.sortedBy { it.currentInstallment ?: 0 }
        val first = sortedBills.first()
        val last = sortedBills.last()
        val paidCount = sortedBills.count { it.isPaid }
        val remainingBills = sortedBills.filter { !it.isPaid }

        return InstallmentGroupDTO(
            id = installment.id!!,
            billName = first.billName,
            billValue = first.billValue,
            totalValue = sortedBills.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.billValue) },
            remainingValue = remainingBills.fold(BigDecimal.ZERO) { acc, b -> acc.add(b.billValue) },
            billCategory = first.billCategory,
            billTable = first.billTable,
            cardId = first.cardId,
            totalInstallments = first.totalInstallments ?: sortedBills.size,
            paidCount = paidCount,
            remainingCount = remainingBills.size,
            startBillDate = first.billDate,
            endBillDate = last.billDate,
            bills = sortedBills.map { it.toBillTableDataDTO() }
        )
    }
}
