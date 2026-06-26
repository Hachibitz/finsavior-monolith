package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.BillRegisterException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.entity.FixedBill
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
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
 * mirroring how installments work. The monthly scheduler keeps generating future
 * months so a fixed bill survives the turn of the year (the previous behavior
 * only inserted rows up to December of the creation year).
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
        val dayOfMonth = parseDayOfMonth(request.purchaseDate)

        val fixedBill = FixedBill(
            user = user,
            billName = request.billName,
            billValue = request.billValue,
            billDescription = request.billDescription,
            billCategory = request.billCategory,
            billType = request.billType,
            billTable = request.billTable,
            paymentType = request.paymentType,
            cardId = request.cardId,
            dayOfMonth = dayOfMonth,
            startBillDate = startBillDate,
            active = true,
            audit = Audit()
        )
        val savedFixedBill = fixedBillRepository.save(fixedBill)

        var firstId: Long? = null
        BillDateUtils.monthsThroughDecember(BillDateUtils.parse(startBillDate)).forEach { billDate ->
            val saved = billTableDataRepository.save(buildInstance(savedFixedBill, billDate))
            if (firstId == null) firstId = saved.id
        }

        log.info("Conta fixa criada (id=${savedFixedBill.id}) com instâncias a partir de $startBillDate")
        return firstId ?: throw BillRegisterException("Não foi possível salvar a conta fixa")
    }

    /**
     * Keeps an up-to-date rolling window of instances (current month → December of
     * the current year) for every active fixed bill. Idempotent: months already
     * present are skipped, so it is safe to run repeatedly.
     */
    @Transactional
    fun generateUpcomingInstancesForAllActive() {
        val activeFixedBills = fixedBillRepository.findAllByActiveTrue()
        if (activeFixedBills.isEmpty()) return

        val current = BillDateUtils.currentMonthYear()
        val targetMonths = BillDateUtils.monthsThroughDecember(current)
        val affectedUserIds = mutableSetOf<Long>()

        activeFixedBills.forEach { fixedBill ->
            val existingMonths = billTableDataRepository
                .findAllByFixedBillId(fixedBill.id!!)
                .map { it.billDate }
                .toSet()

            val missingMonths = targetMonths.filter { it !in existingMonths }
            if (missingMonths.isEmpty()) return@forEach

            missingMonths.forEach { billDate ->
                billTableDataRepository.save(buildInstance(fixedBill, billDate))
            }
            fixedBill.user.id?.let { affectedUserIds.add(it) }
            log.info("Conta fixa ${fixedBill.id}: geradas ${missingMonths.size} instância(s) para ${missingMonths}")
        }

        affectedUserIds.forEach { billCacheService.evictUserCaches(it) }
    }

    private fun buildInstance(fixedBill: FixedBill, billDate: String): BillTableData {
        val purchaseDate: LocalDate? = BillDateUtils.purchaseDateFor(billDate, fixedBill.dayOfMonth)
        return BillTableData(
            id = 0,
            userId = fixedBill.user.id!!,
            billType = fixedBill.billType,
            billDate = billDate,
            billName = fixedBill.billName,
            billValue = fixedBill.billValue,
            billDescription = fixedBill.billDescription,
            billTable = fixedBill.billTable,
            billCategory = fixedBill.billCategory,
            isPaid = false,
            isRecurrent = true,
            paymentType = fixedBill.paymentType,
            cardId = fixedBill.cardId,
            fixedBill = fixedBill,
            purchaseDate = purchaseDate,
            audit = Audit()
        )
    }

    private fun parseDayOfMonth(purchaseDate: String?): Int? {
        if (purchaseDate.isNullOrBlank()) return null
        val normalized = purchaseDate.trim().substringBefore('T')
        return runCatching { LocalDate.parse(normalized).dayOfMonth }.getOrNull()
    }
}
