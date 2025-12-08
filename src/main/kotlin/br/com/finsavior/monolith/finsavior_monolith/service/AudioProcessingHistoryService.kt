package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperLimitException
import br.com.finsavior.monolith.finsavior_monolith.model.entity.AudioProcessingHistory
import br.com.finsavior.monolith.finsavior_monolith.model.enums.AudioProcessingStatus
import br.com.finsavior.monolith.finsavior_monolith.repository.AudioProcessingHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class AudioProcessingHistoryService(
    private val audioProcessingHistoryRepository: AudioProcessingHistoryRepository,
    private val userService: UserService,
) {

    private val log: KLogger = KotlinLogging.logger {  }

    @Transactional
    fun reserveAudioUsage(isUsingCoins: Boolean): AudioProcessingHistory {
        val user = userService.getUserByContext()
        val plan = getPlanTypeById(user.userPlan!!.plan.id)

        if ((plan?.maxAudioBillEntries ?: 0) > 1000) {
            val unlimitedHistory = AudioProcessingHistory(
                userId = user.id!!,
                status = AudioProcessingStatus.PENDING,
                paidWithCoins = false
            )
            return audioProcessingHistoryRepository.save(unlimitedHistory)
        }

        if (!isUsingCoins) {
            val usageCount = countAudioEntriesCurrentMonthFree(user.id!!)
            if (usageCount >= plan!!.maxAudioBillEntries) {
                throw WhisperLimitException("Limite mensal de áudio atingido (${plan.maxAudioBillEntries}/mês). Faça upgrade ou use FSCoins!")
            }
        }

        val history = AudioProcessingHistory(
            userId = user.id!!,
            status = AudioProcessingStatus.PENDING,
            paidWithCoins = isUsingCoins
        )
        return audioProcessingHistoryRepository.save(history)
    }

    @Transactional
    fun updateUsageStatus(history: AudioProcessingHistory, newStatus: AudioProcessingStatus) {
        log.info("M=updateUsageStatus, I=Iniciando atualização de status de uso de áudio.")
        history.status = newStatus
        audioProcessingHistoryRepository.save(history)
    }

    fun countAudioEntriesCurrentMonthFree(userId: Long): Int {
        val now = LocalDateTime.now()
        val startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
        val endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59)

        return audioProcessingHistoryRepository.countByUserIdAndProcessedAtBetweenAndPaidWithCoinsFalse(userId, startOfMonth, endOfMonth)
    }
}