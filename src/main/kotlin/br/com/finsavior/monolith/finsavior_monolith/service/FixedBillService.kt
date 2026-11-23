package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillEntryMethodEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FixedBillGenerationStrategyEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toFixedBill
import br.com.finsavior.monolith.finsavior_monolith.repository.FixedBillRepository
import br.com.finsavior.monolith.finsavior_monolith.util.BillDateUtils
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Owns recurring ("fixed") bills as a single [FixedBill] row per recurring item.
 * Instead of inserting duplicate rows into `bill_table_data` for every future month,
 * active templates are projected on read for any billing month >= [FixedBill.startBillDate]
 * unless explicitly excluded or overridden (e.g. marked paid in a specific month).
 */
@Service
class FixedBillService(
    private val fixedBillRepository: FixedBillRepository
) {

    private val log: KLogger = KotlinLogging.logger {}

    @Transactional
    fun createFixedBill(request: BillTableDataDTO, user: User): Long {
        val savedFixedBill = fixedBillRepository.save(request.toFixedBill(user))
        val id = savedFixedBill.id ?: throw IllegalStateException("Não foi possível salvar a conta fixa")
        log.info("Conta fixa criada (id=$id, startBillDate=${savedFixedBill.startBillDate})")
        return -id
    }

    @Transactional(readOnly = true)
    fun projectFixedBillsForMonth(
        userId: Long,
        billDate: String,
        billTable: BillTableEnum,
        existingBills: List<BillTableDataDTO>,
        cardId: Long? = null
    ): List<BillTableDataDTO> {
        val activeBills = fixedBillRepository.findAllByUserIdAndActiveTrue(userId)
        if (activeBills.isEmpty()) return emptyList()

        val targetMonth = runCatching { BillDateUtils.parse(billDate) }.getOrNull() ?: return emptyList()
        val existingFixedIds = existingBills.mapNotNull { it.fixedBillId }.toSet()

        return activeBills.mapNotNull { fixedBill ->
            val fixedBillId = fixedBill.id ?: return@mapNotNull null
            if (fixedBill.billTable != billTable) return@mapNotNull null
            if (cardId != null && fixedBill.cardId != cardId.toString()) return@mapNotNull null
            if (fixedBillId in existingFixedIds) return@mapNotNull null

            val startMonth = runCatching { BillDateUtils.parse(fixedBill.startBillDate) }.getOrNull()
                ?: return@mapNotNull null
            val isOnOrAfterStart =
                targetMonth.year > startMonth.year ||
                    (targetMonth.year == startMonth.year && targetMonth.monthId >= startMonth.monthId)
            if (!isOnOrAfterStart) return@mapNotNull null

            val excluded = fixedBill.excludedMonths
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
            if (billDate in excluded) return@mapNotNull null

            fixedBill.toProjectedDto(billDate)
        }
    }

    private fun FixedBill.toProjectedDto(billDate: String): BillTableDataDTO =
        BillTableDataDTO(
            id = -this.id!!,
            userId = this.user.id ?: 0L,
            billType = this.billType,
            billDate = billDate,
            billName = this.billName,
            billValue = this.billValue,
            billDescription = this.billDescription,
            billTable = this.billTable,
            billCategory = this.billCategory,
            paid = false,
            isInstallment = false,
            entryMethod = BillEntryMethodEnum.MANUAL,
            isRecurrent = true,
            paymentType = this.paymentType,
            cardId = this.cardId,
            purchaseDate = if (billDate == this.startBillDate) this.purchaseDate?.toString() else null,
            fixedBillGenerationStrategy = FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY,
            fixedBillId = this.id
        )
}
