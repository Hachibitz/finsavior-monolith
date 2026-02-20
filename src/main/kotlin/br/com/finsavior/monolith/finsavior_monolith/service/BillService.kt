package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.BillRegisterException
import br.com.finsavior.monolith.finsavior_monolith.exception.DeleteUserException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CommonEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.MonthEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toBillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toTableData
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAdviceRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class BillService(
    private val billTableDataRepository: BillTableDataRepository,
    private val userService: UserService,
    private val aiAdviceRepository: AiAdviceRepository,
) {

    private val log: KLogger = KotlinLogging.logger {}

    fun billRegister(billRegisterRequestDTO: BillTableDataDTO) {
        val user: User = userService.getUserByContext()

        if (validateBillTable(billRegisterRequestDTO.billTable.name)) {
            throw IllegalArgumentException("Tabela inválida")
        }

        val enrichedRequest = billRegisterRequestDTO.copy(
            billDate = formatBillDate(billRegisterRequestDTO.billDate),
            userId = user.id!!
        )

        try {
            saveRegister(enrichedRequest)
        } catch (e: Exception) {
            log.error("Falha ao salvar o registro: ${e.message}")
            throw BillRegisterException("Erro ao salvar o registro: ${e.message}", e)
        }
    }

    @Transactional
    fun billUpdate(billTableDataDTO: BillTableDataDTO) {
        try {
            val existingRecord = billTableDataRepository.findById(billTableDataDTO.id).orElseThrow {
                IllegalArgumentException("Registro não encontrado")
            }
            existingRecord.apply {
                billName = billTableDataDTO.billName
                billDescription = billTableDataDTO.billDescription
                billValue = billTableDataDTO.billValue
                isPaid = billTableDataDTO.paid
                billCategory = billTableDataDTO.billCategory
                audit?.updateDtm = LocalDateTime.now()
                audit?.updateId = CommonEnum.APP_ID.name
            }
            billTableDataRepository.save(existingRecord)
        } catch (e: Exception) {
            log.error("Falha ao editar item da tabela principal: ${e.message}")
            throw BillRegisterException("Erro ao editar item da tabela principal: ${e.message}", e)
        }
    }

    fun loadMainTableData(billDate: String): List<BillTableDataDTO> =
            billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(
                userService.getUserByContext().id!!,
                this.formatBillDate(billDate),
                BillTableEnum.MAIN
            ).map { it.toBillTableDataDTO() }

    fun loadCardTableData(billDate: String): List<BillTableDataDTO> =
        billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(
            userService.getUserByContext().id!!,
            this.formatBillDate(billDate),
            BillTableEnum.CREDIT_CARD
        ).map { it.toBillTableDataDTO() }

    fun loadAssetsTableData(billDate: String): List<BillTableDataDTO> =
        billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(
            userService.getUserByContext().id!!,
            this.formatBillDate(billDate),
            BillTableEnum.ASSETS
        ).map { it.toBillTableDataDTO() }

    fun loadPaymentCardTableData(billDate: String): List<BillTableDataDTO> =
        billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(
            userService.getUserByContext().id!!,
            this.formatBillDate(billDate),
            BillTableEnum.PAYMENT_CARD
        ).map { it.toBillTableDataDTO() }

    fun deleteItemFromTable(itemId: Long) =
        billTableDataRepository.deleteById(itemId)

    @Transactional
    fun deleteAllUserData(userId: Long) {
        try {
            billTableDataRepository.deleteByUserId(userId)
            aiAdviceRepository.deleteByUserId(userId)
        } catch (e: Exception) {
            log.error("Falha ao deletar dados do usuário: ${e.message}")
            throw DeleteUserException(e.message?:"Erro ao deletar dados do usuário")
        }
    }

    @Transactional
    fun batchRegister(requests: List<BillTableDataDTO>) {
        val user = userService.getUserByContext()

        val entities = requests.map { request ->
            val enrichedRequest = request.copy(
                billDate = formatBillDate(request.billDate),
                userId = user.id!!,
                billDescription = if (request.isInstallment == true && request.currentInstallment != null)
                    "${request.billDescription} | Via Importação (${request.currentInstallment}/${request.installmentCount})"
                else request.billDescription
            )

            val entity = enrichedRequest.toTableData()
            entity.audit = Audit()
            entity
        }

        billTableDataRepository.saveAll(entities)
        log.info("Lote de ${entities.size} contas importado com sucesso para o usuário ${user.id}")
    }

    private fun validateBillTable(billTable: String?): Boolean {
        return BillTableEnum.entries.none { it.name == billTable }
    }

    private fun saveRegister(request: BillTableDataDTO) {
        when {
            request.isRecurrent == true -> saveRecurrentRegister(request)
            request.isInstallment == true -> saveInstallmentRegister(request)
            else -> saveSingleRegister(request)
        }
    }

    @Transactional
    private fun saveRecurrentRegister(request: BillTableDataDTO) {
        val requestMonth = request.billDate.split(" ")[0]
        val requestYear: String = request.billDate.split(" ")[1]
        val monthId: Int = MonthEnum.valueOf(requestMonth.uppercase(Locale.getDefault())).id

        MonthEnum.entries.stream()
            .filter { month -> month.id >= monthId }
            .forEach { month ->
                val register: BillTableData = request.toTableData()
                register.audit = Audit()
                register.billDate = "${month.value} $requestYear"

                billTableDataRepository.save(register)
                log.info("Registro recorrente salvo: $register")
            }
    }

    @Transactional
    private fun saveInstallmentRegister(request: BillTableDataDTO) {
        val totalInstallments = request.installmentCount ?: 2
        var currentBillDate = request.billDate

        for (i in 1..totalInstallments) {
            val register: BillTableData = request.toTableData()
            register.audit = Audit()

            register.billDate = currentBillDate
            register.totalInstallments = totalInstallments
            register.currentInstallment = i
            register.billDescription = " ${register.billDescription} | Parcela ($i/$totalInstallments)"

            billTableDataRepository.save(register)
            log.info("Parcela $i/$totalInstallments salva: $register")

            currentBillDate = addOneMonthToDateString(currentBillDate)
        }
    }

    private fun saveSingleRegister(request: BillTableDataDTO) {
        val register: BillTableData = request.toTableData()
        register.audit = Audit()

        billTableDataRepository.save(register)
        log.info("Registro único salvo: $register")
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
        if (billDate.length == 7 && !billDate.contains(" ") && billDate.contains(Regex("[A-Za-z]"))) {
            return "${billDate.take(3)} ${billDate.substring(3)}"
        }

        if (billDate.length == 8 && billDate.contains(" ")) {
            return billDate
        }

        val datePatterns = listOf(
            "dd/MM/yyyy",
            "yyyy-MM-dd",
            "dd-MM-yyyy",
            "MM/dd/yyyy"
        )

        for (pattern in datePatterns) {
            try {
                val date = LocalDate.parse(billDate, DateTimeFormatter.ofPattern(pattern))
                return formatDateToMonthYear(date)
            } catch (_: Exception) {
                continue
            }
        }

        log.warn("Formato de data não reconhecido: $billDate")
        return billDate
    }

    private fun formatDateToMonthYear(date: LocalDate): String {
        val formattedMonth = date.month.toString()
            .take(3)
            .lowercase()
            .replaceFirstChar(Char::uppercase)
        return "$formattedMonth ${date.year}"
    }
}