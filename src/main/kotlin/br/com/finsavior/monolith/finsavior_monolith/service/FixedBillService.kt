package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.BillRegisterException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FixedBillGenerationStrategyEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toBillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toFixedBill
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.FixedBillRepository
import br.com.finsavior.monolith.finsavior_monolith.util.BillDateUtils
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/**
 * Owns the lifecycle of recurring ("fixed") bills. A [FixedBill] template is
 * created once and a real [BillTableData] row is materialized for each month,
 * mirroring how installments work. The generation strategy is user-selected:
 * annual upfront creates all remaining months through December, while monthly
 * first-day creates only one instance per month.
 */
@Service
class FixedBillService(
    private val fixedBillRepository: FixedBillRepository,
    private val billTableDataRepository: BillTableDataRepository,
    private val billCacheService: BillCacheService
) {

    private val log: KLogger = KotlinLogging.logger {}

    @Transactional
    fun createFixedBill(request: BillTableDataDTO, user: User): Long {
        val startBillDate = request.billDate
        val generationStrategy = request.fixedBillGenerationStrategy
            ?: FixedBillGenerationStrategyEnum.YEARLY_UPFRONT

        val savedFixedBill = fixedBillRepository.save(request.toFixedBill(user))

        var firstId: Long? = null
        val initialMonths = when (generationStrategy) {
            FixedBillGenerationStrategyEnum.YEARLY_UPFRONT ->
                BillDateUtils.monthsThroughDecember(BillDateUtils.parse(startBillDate))
            FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY ->
                listOf(startBillDate)
        }

        initialMonths.forEach { billDate ->
            val instance = savedFixedBill.toBillTableData(billDate)
            if (billDate == startBillDate && !request.purchaseDate.isNullOrBlank()) {
                instance.purchaseDate = LocalDate.parse(request.purchaseDate!!.trim().substringBefore('T'))
            }
            val saved = billTableDataRepository.save(instance)
            if (firstId == null) firstId = saved.id
        }

        log.info("Conta fixa criada (id=${savedFixedBill.id}, strategy=$generationStrategy) com instâncias $initialMonths")
        return firstId ?: throw BillRegisterException("Não foi possível salvar a conta fixa")
    }

    /**
     * Runs once a year for fixed bills that should be pre-generated through December.
     * Idempotent: months already present are skipped.
     */
    @Transactional
    fun generateYearlyInstancesForActiveBills() {
        val activeFixedBills = fixedBillRepository.findAllByActiveTrue()
            .filter { it.generationStrategy == FixedBillGenerationStrategyEnum.YEARLY_UPFRONT }
        if (activeFixedBills.isEmpty()) return

        val current = BillDateUtils.currentMonthYear()
        val targetMonths = BillDateUtils.monthsThroughDecember(current)
        generateMissingInstances(activeFixedBills, targetMonths)
    }

    /**
     * Runs every month for fixed bills that should only be materialized on demand,
     * one month at a time. If the parent fixed bill is deleted, no future instance
     * is generated.
     */
    @Transactional
    fun generateCurrentMonthInstancesForActiveMonthlyBills() {
        val activeFixedBills = fixedBillRepository.findAllByActiveTrue()
            .filter { it.generationStrategy == FixedBillGenerationStrategyEnum.MONTHLY_FIRST_DAY }
        if (activeFixedBills.isEmpty()) return

        val currentMonth = BillDateUtils.currentMonthYear().toBillDate()
        generateMissingInstances(activeFixedBills, listOf(currentMonth))
    }

    /**
     * Ensures every active fixed bill for [userId] has a materialized row for [billDate]
     * whenever [billDate] is at or after the template's start month and was not explicitly excluded.
     * Allows monthly and cross-year fixed bills to appear in future-month planning and totals.
     */
    @Transactional
    fun ensureInstancesForUserAndMonth(userId: Long, billDate: String) {
        val activeBills = fixedBillRepository.findAllByUserIdAndActiveTrue(userId)
        if (activeBills.isEmpty()) return

        val targetMonth = runCatching { BillDateUtils.parse(billDate) }.getOrNull() ?: return

        activeBills.forEach { fixedBill ->
            val startMonth = runCatching { BillDateUtils.parse(fixedBill.startBillDate) }.getOrNull() ?: return@forEach
            val isOnOrAfterStart =
                targetMonth.year > startMonth.year ||
                    (targetMonth.year == startMonth.year && targetMonth.monthId >= startMonth.monthId)
            if (!isOnOrAfterStart) return@forEach

            val excluded = fixedBill.excludedMonths
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                ?: emptySet()
            if (billDate in excluded) return@forEach

            val fixedBillId = fixedBill.id ?: return@forEach
            val alreadyExists = billTableDataRepository
                .findAllByFixedBillId(fixedBillId)
                .any { it.billDate == billDate }
            if (alreadyExists) return@forEach

            billTableDataRepository.save(fixedBill.toBillTableData(billDate))
        }
    }

    private fun generateMissingInstances(fixedBills: List<FixedBill>, targetMonths: List<String>) {
        val affectedUserIds = mutableSetOf<Long>()

        fixedBills.forEach { fixedBill ->
            val existingMonths = billTableDataRepository
                .findAllByFixedBillId(fixedBill.id!!)
                .map { it.billDate }
                .toSet()

            val missingMonths = targetMonths.filter { it !in existingMonths }
            if (missingMonths.isEmpty()) return@forEach

            missingMonths.forEach { billDate ->
                billTableDataRepository.save(fixedBill.toBillTableData(billDate))
            }
            fixedBill.user.id?.let { affectedUserIds.add(it) }
            log.info("Conta fixa ${fixedBill.id}: geradas ${missingMonths.size} instância(s) para ${missingMonths}")
        }

        affectedUserIds.forEach { billCacheService.evictUserCaches(it) }
    }
}
