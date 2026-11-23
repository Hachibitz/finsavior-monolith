package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.BillRegisterException
import br.com.finsavior.monolith.finsavior_monolith.exception.DeleteUserException
import br.com.finsavior.monolith.finsavior_monolith.exception.UnauthorizedException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Installment
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CommonEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.MonthEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toBillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toTableData
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAdviceRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.FixedBillRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.InstallmentRepository
import mu.KLogger
import mu.KotlinLogging
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class BillService(
    private val billTableDataRepository: BillTableDataRepository,
    private val userService: UserService,
    private val aiAdviceRepository: AiAdviceRepository,
    private val installmentRepository: InstallmentRepository,
    private val fixedBillRepository: FixedBillRepository,
    private val fixedBillService: FixedBillService,
    private val billCacheService: BillCacheService
) {

    private val log: KLogger = KotlinLogging.logger {}

    companion object {
        private const val MAX_BILL_NAME_LENGTH = 100
        private const val MAX_BILL_DESCRIPTION_LENGTH = 255
    }

    fun billRegister(billRegisterRequestDTO: BillTableDataDTO): Long {
        val user: User = userService.getUserByContext()

        if (validateBillTable(billRegisterRequestDTO.billTable.name)) {
            throw IllegalArgumentException("Tabela inválida")
        }

        val enrichedRequest = sanitize(billRegisterRequestDTO).copy(
            billDate = formatBillDate(billRegisterRequestDTO.billDate),
            userId = user.id!!
        )

        try {
            val resultId = saveRegister(enrichedRequest, user)
            billCacheService.evictUserCaches(user.id!!)
            return resultId
        } catch (e: Exception) {
            log.error("Falha ao salvar o registro: ${e.message}")
            throw BillRegisterException("Erro ao salvar o registro: ${e.message}", e)
        }
    }

    @Transactional
    fun billUpdate(billTableDataDTO: BillTableDataDTO) {
        val user = userService.getUserByContext()
        try {
            val id = billTableDataDTO.id ?: throw IllegalArgumentException("ID não informado")
            val sanitized = sanitize(billTableDataDTO)

            // Negative IDs represent virtual projections from FixedBill.
            if (id < 0) {
                val fixedBill = fixedBillRepository.findById(-id).orElseThrow {
                    IllegalArgumentException("Registro não encontrado")
                }
                if (fixedBill.user.id != user.id) {
                    throw UnauthorizedException("Você não tem permissão para alterar este registro")
                }
                fixedBill.apply {
                    billName = sanitized.billName
                    billDescription = sanitized.billDescription
                    billValue = sanitized.billValue
                    billCategory = sanitized.billCategory
                    paymentType = sanitized.paymentType
                    cardId = sanitized.cardId
                    parsePurchaseDate(sanitized.purchaseDate)?.let {
                        dayOfMonth = it.dayOfMonth
                        if (formatBillDate(sanitized.billDate) == startBillDate) {
                            purchaseDate = it
                        }
                    }
                    audit?.updateDtm = LocalDateTime.now()
                }
                fixedBillRepository.save(fixedBill)

                // Materialize a month row only when paid is toggled on so monthly payment status is persisted.
                if (sanitized.paid) {
                    val monthRow = BillTableData(
                        id = 0,
                        userId = user.id!!,
                        billType = fixedBill.billType,
                        billDate = formatBillDate(sanitized.billDate),
                        billName = fixedBill.billName,
                        billValue = fixedBill.billValue,
                        billDescription = fixedBill.billDescription,
                        billTable = fixedBill.billTable,
                        billCategory = fixedBill.billCategory,
                        isPaid = true,
                        isRecurrent = true,
                        paymentType = fixedBill.paymentType,
                        cardId = fixedBill.cardId,
                        fixedBill = fixedBill,
                        purchaseDate = parsePurchaseDate(sanitized.purchaseDate),
                        audit = Audit()
                    )
                    billTableDataRepository.save(monthRow)
                }

                billCacheService.evictUserCaches(user.id!!)
                return
            }

            val existingRecord = billTableDataRepository.findById(id).orElseThrow {
                IllegalArgumentException("Registro não encontrado")
            }
            if (existingRecord.userId != user.id) {
                throw UnauthorizedException("Você não tem permissão para alterar este registro")
            }
            existingRecord.apply {
                billDate = formatBillDate(sanitized.billDate)
                billName = sanitized.billName
                billDescription = sanitized.billDescription
                billValue = sanitized.billValue
                isPaid = sanitized.paid
                billCategory = sanitized.billCategory
                paymentType = sanitized.paymentType
                cardId = sanitized.cardId
                parsePurchaseDate(sanitized.purchaseDate)?.let { purchaseDate = it }
                audit?.updateDtm = LocalDateTime.now()
                audit?.updateId = CommonEnum.APP_ID.name
            }
            billTableDataRepository.save(existingRecord)
            syncFixedBillTemplate(existingRecord)
            billCacheService.evictUserCaches(user.id!!)
        } catch (e: UnauthorizedException) {
            throw e
        } catch (e: Exception) {
            log.error("Falha ao editar item da tabela principal: ${e.message}")
            throw BillRegisterException("Erro ao editar item da tabela principal: ${e.message}", e)
        }
    }

    /** Keeps the fixed-bill template in sync when one of its instances is edited, so future months reflect the change. */
    private fun syncFixedBillTemplate(bill: BillTableData) {
        val fixedBill = bill.fixedBill ?: return
        fixedBill.apply {
            billName = bill.billName
            billValue = bill.billValue
            billDescription = bill.billDescription
            billCategory = bill.billCategory
            paymentType = bill.paymentType
            cardId = bill.cardId
            bill.purchaseDate?.let { dayOfMonth = it.dayOfMonth }
            audit?.updateDtm = LocalDateTime.now()
        }
        fixedBillRepository.save(fixedBill)
    }

    private fun parsePurchaseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        val normalized = value.trim().substringBefore('T')
        return runCatching { LocalDate.parse(normalized) }.getOrNull()
    }

    /** Defense-in-depth: trims and bounds free-text fields for paths that bypass bean validation (AI import, batch). */
    private fun sanitize(request: BillTableDataDTO): BillTableDataDTO =
        request.copy(
            billName = request.billName.trim().take(MAX_BILL_NAME_LENGTH),
            billDescription = request.billDescription?.trim()?.take(MAX_BILL_DESCRIPTION_LENGTH)
        )

    @Cacheable(value = ["mainTable"], key = "@userService.getUserByContext().id + '-' + #billDate")
    fun loadMainTableData(billDate: String): List<BillTableDataDTO> {
        val userId = userService.getUserByContext().id!!
        val formattedDate = this.formatBillDate(billDate)
        val stored = billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(
            userId,
            formattedDate,
            BillTableEnum.MAIN
        ).map { it.toBillTableDataDTO() }
        return stored + fixedBillService.projectFixedBillsForMonth(userId, formattedDate, BillTableEnum.MAIN, stored)
    }

    @Cacheable(value = ["cardTable"], key = "@userService.getUserByContext().id + '-' + #billDate")
    fun loadCardTableData(billDate: String): List<BillTableDataDTO> {
        val userId = userService.getUserByContext().id!!
        val formattedDate = this.formatBillDate(billDate)
        val stored = billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(
            userId,
            formattedDate,
            BillTableEnum.CREDIT_CARD
        ).map { it.toBillTableDataDTO() }
        return stored + fixedBillService.projectFixedBillsForMonth(userId, formattedDate, BillTableEnum.CREDIT_CARD, stored)
    }

    @Cacheable(value = ["cardExpenses"], key = "@userService.getUserByContext().id + '-' + #billDate + '-' + #cardId")
    fun loadCardExpenses(billDate: String, cardId: Long): List<BillTableDataDTO> {
        val userId = userService.getUserByContext().id!!
        val formattedDate = this.formatBillDate(billDate)
        val stored = billTableDataRepository.findAllByUserIdAndBillDateAndBillTableAndCardId(
            userId,
            formattedDate,
            BillTableEnum.CREDIT_CARD,
            cardId.toString()
        ).map { it.toBillTableDataDTO() }
        return stored + fixedBillService.projectFixedBillsForMonth(userId, formattedDate, BillTableEnum.CREDIT_CARD, stored, cardId)
    }

    @Cacheable(value = ["assetsTable"], key = "@userService.getUserByContext().id + '-' + #billDate")
    fun loadAssetsTableData(billDate: String): List<BillTableDataDTO> {
        val userId = userService.getUserByContext().id!!
        val formattedDate = this.formatBillDate(billDate)
        val stored = billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(
            userId,
            formattedDate,
            BillTableEnum.ASSETS
        ).map { it.toBillTableDataDTO() }
        return stored + fixedBillService.projectFixedBillsForMonth(userId, formattedDate, BillTableEnum.ASSETS, stored)
    }

    @Cacheable(value = ["paymentCardTable"], key = "@userService.getUserByContext().id + '-' + #billDate")
    fun loadPaymentCardTableData(billDate: String): List<BillTableDataDTO> =
        billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(
            userService.getUserByContext().id!!,
            this.formatBillDate(billDate),
            BillTableEnum.PAYMENT_CARD
        ).map { it.toBillTableDataDTO() }

    @Transactional
    fun deleteItem(itemId: Long, deleteAll: Boolean, billDate: String? = null) {
        val user = userService.getUserByContext()

        if (itemId < 0) {
            val fixedBill = fixedBillRepository.findById(-itemId)
                .orElseThrow { IllegalArgumentException("Registro não encontrado") }
            if (fixedBill.user.id != user.id) {
                throw UnauthorizedException("Você não tem permissão para excluir este registro")
            }
            if (deleteAll || billDate.isNullOrBlank()) {
                fixedBillRepository.delete(fixedBill)
            } else {
                val formatted = formatBillDate(billDate)
                val current = fixedBill.excludedMonths
                    ?.split(",")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?.toMutableSet()
                    ?: mutableSetOf()
                if (current.add(formatted)) {
                    fixedBill.excludedMonths = current.joinToString(",")
                    fixedBillRepository.save(fixedBill)
                }
            }
            billCacheService.evictUserCaches(user.id!!)
            return
        }

        val bill = billTableDataRepository.findById(itemId)
            .orElseThrow { IllegalArgumentException("Registro não encontrado") }

        if (bill.userId != user.id) {
            throw UnauthorizedException("Você não tem permissão para excluir este registro")
        }

        when {
            deleteAll && bill.installment != null -> installmentRepository.delete(bill.installment!!)
            deleteAll && bill.fixedBill != null -> fixedBillRepository.delete(bill.fixedBill!!)
            else -> {
                markFixedBillMonthExcluded(bill)
                billTableDataRepository.delete(bill)
            }
        }
        billCacheService.evictUserCaches(user.id!!)
    }

    @Transactional
    fun deleteItemFromTable(itemId: Long) {
        if (itemId < 0) {
            deleteItem(itemId, deleteAll = true)
            return
        }
        val user = userService.getUserByContext()
        val bill = billTableDataRepository.findById(itemId)
            .orElseThrow { IllegalArgumentException("Registro não encontrado") }
        if (bill.userId != user.id) {
            throw UnauthorizedException("Você não tem permissão para excluir este registro")
        }
        markFixedBillMonthExcluded(bill)
        billTableDataRepository.delete(bill)
        billCacheService.evictUserCaches(user.id!!)
    }

    private fun markFixedBillMonthExcluded(bill: BillTableData) {
        val fixedBill = bill.fixedBill ?: return
        val current = fixedBill.excludedMonths
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toMutableSet()
            ?: mutableSetOf()
        if (current.add(bill.billDate)) {
            fixedBill.excludedMonths = current.joinToString(",")
            fixedBillRepository.save(fixedBill)
        }
    }

    @Transactional
    fun deleteAllUserData(userId: Long) {
        try {
            billTableDataRepository.deleteByUserId(userId)
            fixedBillRepository.deleteByUserId(userId)
            aiAdviceRepository.deleteByUserId(userId)
            billCacheService.evictUserCaches(userId)
        } catch (e: Exception) {
            log.error("Falha ao deletar dados do usuário: ${e.message}")
            throw DeleteUserException(e.message?:"Erro ao deletar dados do usuário")
        }
    }

    @Transactional
    fun batchRegister(requests: List<BillTableDataDTO>) {
        val user = userService.getUserByContext()
        requests.forEach { request ->
            val enrichedRequest = sanitize(request).copy(
                billDate = formatBillDate(request.billDate),
                userId = user.id!!,
                billDescription = if (request.isInstallment == true && request.currentInstallment != null)
                    "${request.billDescription} | Via Importação (${request.currentInstallment}/${request.installmentCount})"
                else request.billDescription
            )
            saveRegister(enrichedRequest, user)
        }
        billCacheService.evictUserCaches(user.id!!)
        log.info("Lote de ${requests.size} contas importado com sucesso para o usuário ${user.id}")
    }

    private fun validateBillTable(billTable: String?): Boolean {
        return BillTableEnum.entries.none { it.name == billTable }
    }

    private fun saveRegister(request: BillTableDataDTO, user: User): Long {
        return when {
            request.isRecurrent == true -> fixedBillService.createFixedBill(request, user)
            request.isInstallment == true -> saveInstallmentRegister(request, user)
            else -> saveSingleRegister(request)
        }
    }

    @Transactional
    private fun saveInstallmentRegister(request: BillTableDataDTO, user: User): Long {
        val totalInstallments = request.installmentCount ?: 1
        var currentBillDate = request.billDate
        val purchaseDate: LocalDate? = parsePurchaseDate(request.purchaseDate)
        var firstId: Long? = null

        val installment = Installment(user = user)
        val savedInstallment = installmentRepository.save(installment)

        for (i in (request.currentInstallment ?: 1)..totalInstallments) {
            val register: BillTableData = request.toTableData()
            register.audit = Audit()
            register.billDate = currentBillDate
            register.totalInstallments = totalInstallments
            register.currentInstallment = i
            register.billDescription = " ${request.billDescription} | Parcela ($i/$totalInstallments)"
            register.installment = savedInstallment
            register.purchaseDate = purchaseDate

            val saved = billTableDataRepository.save(register)
            if (firstId == null) {
                firstId = saved.id
            }

            log.info("Parcela $i/$totalInstallments salva: $saved")

            currentBillDate = addOneMonthToDateString(currentBillDate)
        }

        return firstId ?: throw BillRegisterException("Não foi possível salvar as parcelas")
    }

    private fun saveSingleRegister(request: BillTableDataDTO): Long {
        val register: BillTableData = request.toTableData()
        register.audit = Audit()

        val saved = billTableDataRepository.save(register) ?: throw BillRegisterException("Não foi possível salvar o registro")
        log.info("Registro único salvo: $saved")
        return saved.id
    }

    private fun addOneMonthToDateString(dateString: String): String {
        val parts = dateString.split(" ")
        val monthStr = parts[0].uppercase(Locale.getDefault())
        val yearStr = parts[1]

        val monthEnum = MonthEnum.valueOf(monthStr)
        var monthId = monthEnum.id
        var yearInt = yearStr.toInt()

        monthId++

        if (monthId > 12) {
            monthId = 1
            yearInt++
        }

        val newMonthEnum = MonthEnum.entries.first { it.id == monthId }

        return "${newMonthEnum.value} $yearInt"
    }

    fun formatBillDate(billDate: String): String {
        val input = billDate.trim()

        if (input.length == 7 && !input.contains(" ") && input.contains(Regex("[A-Za-z]"))) {
            return "${input.take(3)} ${input.substring(3)}"
        }

        if (input.length == 8 && input.contains(" ")) {
            return input
        }

        val yearMonthPatterns = listOf("yyyy-MM")
        yearMonthPatterns.forEach { pattern ->
            runCatching {
                val ym = YearMonth.parse(input, DateTimeFormatter.ofPattern(pattern))
                return formatDateToMonthYear(ym)
            }
        }

        val datePatterns = listOf(
            "dd/MM/yyyy",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "MM/dd/yyyy"
        )

        datePatterns.forEach { pattern ->
            runCatching {
                val date = LocalDate.parse(input, DateTimeFormatter.ofPattern(pattern))
                return formatDateToMonthYear(date)
            }
        }

        log.warn("Formato de data não reconhecido: $billDate")
        return billDate
    }

    private fun formatDateToMonthYear(date: LocalDate): String {
        return formatMonthYear(date.month, date.year)
    }

    private fun formatDateToMonthYear(yearMonth: YearMonth): String {
        return formatMonthYear(yearMonth.month, yearMonth.year)
    }

    private fun formatMonthYear(month: java.time.Month, year: Int): String {
        val formattedMonth = month.toString()
            .take(3)
            .lowercase()
            .replaceFirstChar(Char::uppercase)
        return "$formattedMonth $year"
    }
}