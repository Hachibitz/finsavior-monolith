package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.BillRegisterException
import br.com.finsavior.monolith.finsavior_monolith.exception.UnauthorizedException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.FixedBillDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpdateFixedBillRequest
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CommonEnum
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
import java.time.LocalDateTime
import java.time.YearMonth

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
    private val billCacheService: BillCacheService,
    private val userService: UserService
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

    @Transactional(readOnly = true)
    fun listFixedBills(): List<FixedBillDTO> {
        val user = userService.getUserByContext()
        return fixedBillRepository.findAllByUserId(user.id!!)
            .sortedByDescending { it.id }
            .map { toDTO(it) }
    }

    @Transactional
    fun updateFixedBill(id: Long, request: UpdateFixedBillRequest): FixedBillDTO {
        val user = userService.getUserByContext()
        val fixedBill = getOwnedFixedBill(id, user.id!!)

        fixedBill.apply {
            billName = request.billName.trim().take(100)
            billValue = request.billValue
            billDescription = request.billDescription?.trim()?.take(255)
            billCategory = request.billCategory
            request.billTable?.let { billTable = it }
            cardId = request.cardId
            dayOfMonth = request.dayOfMonth
            request.generationStrategy?.let { generationStrategy = it }
            request.active?.let { active = it }
            audit?.updateDtm = LocalDateTime.now()
            audit?.updateId = CommonEnum.APP_ID.name
        }

        val saved = fixedBillRepository.save(fixedBill)

        if (request.applyToFutureOpenBills) {
            val currentYm = YearMonth.now()
            val instances = billTableDataRepository.findAllByFixedBillId(saved.id!!)
            instances.filter { isCurrentOrFutureOpenBill(it, currentYm) }.forEach { instance ->
                instance.apply {
                    billName = saved.billName
                    billValue = saved.billValue
                    billDescription = saved.billDescription
                    billCategory = saved.billCategory
                    billTable = saved.billTable
                    cardId = saved.cardId
                    audit?.updateDtm = LocalDateTime.now()
                    audit?.updateId = CommonEnum.APP_ID.name
                }
                billTableDataRepository.save(instance)
            }
        }

        if (saved.active && saved.generationStrategy == FixedBillGenerationStrategyEnum.YEARLY_UPFRONT) {
            val targetMonths = BillDateUtils.monthsThroughDecember(BillDateUtils.currentMonthYear())
            generateMissingInstances(listOf(saved), targetMonths)
        }

        billCacheService.evictUserCaches(user.id!!)
        return toDTO(saved)
    }

    @Transactional
    fun migrateStrategy(id: Long, strategy: FixedBillGenerationStrategyEnum): FixedBillDTO {
        val user = userService.getUserByContext()
        val fixedBill = getOwnedFixedBill(id, user.id!!)

        fixedBill.generationStrategy = strategy
        fixedBill.audit?.updateDtm = LocalDateTime.now()
        fixedBill.audit?.updateId = CommonEnum.APP_ID.name

        val saved = fixedBillRepository.save(fixedBill)

        if (saved.active && strategy == FixedBillGenerationStrategyEnum.YEARLY_UPFRONT) {
            val targetMonths = BillDateUtils.monthsThroughDecember(BillDateUtils.currentMonthYear())
            generateMissingInstances(listOf(saved), targetMonths)
        }

        billCacheService.evictUserCaches(user.id!!)
        return toDTO(saved)
    }

    @Transactional
    fun toggleActive(id: Long, active: Boolean): FixedBillDTO {
        val user = userService.getUserByContext()
        val fixedBill = getOwnedFixedBill(id, user.id!!)

        fixedBill.active = active
        fixedBill.audit?.updateDtm = LocalDateTime.now()
        fixedBill.audit?.updateId = CommonEnum.APP_ID.name

        val saved = fixedBillRepository.save(fixedBill)

        if (saved.active && saved.generationStrategy == FixedBillGenerationStrategyEnum.YEARLY_UPFRONT) {
            val targetMonths = BillDateUtils.monthsThroughDecember(BillDateUtils.currentMonthYear())
            generateMissingInstances(listOf(saved), targetMonths)
        }

        billCacheService.evictUserCaches(user.id!!)
        return toDTO(saved)
    }

    @Transactional
    fun deleteFixedBill(id: Long, deleteFutureOnly: Boolean) {
        val user = userService.getUserByContext()
        val fixedBill = getOwnedFixedBill(id, user.id!!)

        if (deleteFutureOnly) {
            val currentYm = YearMonth.now()
            val instances = billTableDataRepository.findAllByFixedBillId(fixedBill.id!!)
            val toDelete = instances.filter { isCurrentOrFutureOpenBill(it, currentYm) }
            val toKeep = instances.filterNot { isCurrentOrFutureOpenBill(it, currentYm) }

            toKeep.forEach { bill ->
                bill.fixedBill = null
                bill.audit?.updateDtm = LocalDateTime.now()
                billTableDataRepository.save(bill)
            }
            fixedBill.bills.clear()
            billTableDataRepository.deleteAll(toDelete)
            fixedBillRepository.delete(fixedBill)
        } else {
            fixedBillRepository.delete(fixedBill)
        }

        billCacheService.evictUserCaches(user.id!!)
    }

    private fun getOwnedFixedBill(id: Long, userId: Long): FixedBill {
        val fixedBill = fixedBillRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Conta fixa não encontrada") }
        if (fixedBill.user.id != userId) {
            throw UnauthorizedException("Você não tem permissão para acessar esta conta fixa")
        }
        return fixedBill
    }

    private fun isCurrentOrFutureOpenBill(bill: BillTableData, currentYm: YearMonth): Boolean {
        if (bill.isPaid) return false
        val billYm = runCatching {
            val my = BillDateUtils.parse(bill.billDate)
            YearMonth.of(my.year, my.monthId)
        }.getOrNull() ?: return true
        return !billYm.isBefore(currentYm)
    }

    private fun toDTO(fixedBill: FixedBill): FixedBillDTO {
        val instances = billTableDataRepository.findAllByFixedBillId(fixedBill.id!!)
        return FixedBillDTO(
            id = fixedBill.id!!,
            billName = fixedBill.billName,
            billValue = fixedBill.billValue,
            billDescription = fixedBill.billDescription,
            billCategory = fixedBill.billCategory,
            billType = fixedBill.billType,
            billTable = fixedBill.billTable,
            paymentType = fixedBill.paymentType,
            cardId = fixedBill.cardId,
            dayOfMonth = fixedBill.dayOfMonth,
            startBillDate = fixedBill.startBillDate,
            generationStrategy = fixedBill.generationStrategy,
            active = fixedBill.active,
            instanceCount = instances.size,
            paidCount = instances.count { it.isPaid }
        )
    }
}
