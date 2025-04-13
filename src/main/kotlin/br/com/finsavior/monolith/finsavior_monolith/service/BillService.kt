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
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@Service
class BillService(
    private val billTableDataRepository: BillTableDataRepository,
    private val userService: UserService,
    private val aiAdviceRepository: AiAdviceRepository
) {

    private val log: KLogger = KotlinLogging.logger {}

    fun billRegister(billRegisterRequestDTO: BillTableDataDTO) {
        val user: User = userService.getUserByContext()

        if (validateBillTable(billRegisterRequestDTO.billTable.name)) {
            throw IllegalArgumentException("Tabela inválida")
        }

        billRegisterRequestDTO.billDate = formatBillDate(billRegisterRequestDTO.billDate)
        billRegisterRequestDTO.userId = user.id!!

        try {
            saveRegister(billRegisterRequestDTO)
        } catch (e: Exception) {
            log.error("Falha ao salvar o registro: ${e.message}")
            throw BillRegisterException("Erro ao salvar o registro: ${e.message}", e)
        }
    }

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

    private fun validateBillTable(billTable: String?): Boolean {
        val isError = AtomicBoolean(false)
        var typeChecker = 0
        val tableEnum: Array<BillTableEnum> = BillTableEnum.entries.toTypedArray()

        for (tableType in tableEnum) {
            typeChecker = if (tableType.name == billTable) typeChecker + 1 else typeChecker + 0
            isError.set(typeChecker == 0)
        }

        return isError.get()
    }

    private fun saveRegister(request: BillTableDataDTO) {
        if (request.isRecurrent == true) {
            saveRecurrentRegister(request)
        } else {
            saveSingleRegister(request)
        }
    }

    @Transactional
    private fun saveRecurrentRegister(request: BillTableDataDTO) {
        val requestMonth = request.billDate.split(" ")[0]
        val requestYear: String? = request.billDate.split(" ")[1]
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

    private fun saveSingleRegister(request: BillTableDataDTO) {
        val register: BillTableData = request.toTableData()
        register.audit = Audit()

        billTableDataRepository.save(register)
        log.info("Registro único salvo: $register")
    }

    fun formatBillDate(billDate: String): String {
        var billDate = billDate
        if (billDate.length == 7) {
            billDate = billDate.substring(0, 3) + " " + billDate.substring(3, 7)
        }

        return billDate
    }
}