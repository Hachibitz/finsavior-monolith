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
            val saved = billTableDataRepository.save(savedFixedBill.toBillTableData(billDate))
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
