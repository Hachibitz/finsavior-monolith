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
import kotlin.math.ceil

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
            ?: throw WhisperLimitException("Plano não encontrado para validação de áudio.")

        if (!isUsingCoins) {
            val usageCount = countAudioEntriesCurrentMonthFree(user.id!!)
            if (usageCount >= plan.maxAudioBillEntries && plan.maxAudioBillEntries < Int.MAX_VALUE) {
                throw WhisperLimitException(
                    "Limite mensal de áudios atingido (${plan.maxAudioBillEntries}/mês). Faça upgrade ou use FSCoins!"
                )
            }

            val usedMinutes = usedAudioMinutesCurrentMonthFree(user.id!!)
            if (usedMinutes >= plan.maxAudioMinutesPerMonth && plan.maxAudioMinutesPerMonth < Int.MAX_VALUE) {
                throw WhisperLimitException(
                    "Limite mensal de minutos de áudio atingido (${plan.maxAudioMinutesPerMonth} min/mês). Faça upgrade ou use FSCoins!"
                )
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
    fun completeAudioUsage(history: AudioProcessingHistory, durationSeconds: Int, newStatus: AudioProcessingStatus) {
        if (!history.paidWithCoins && newStatus == AudioProcessingStatus.SUCCESS) {
            val user = userService.getUserByContext()
            val plan = getPlanTypeById(user.userPlan!!.plan.id)
            if (plan != null && plan.maxAudioMinutesPerMonth < Int.MAX_VALUE) {
                val usedSeconds = sumDurationSecondsCurrentMonthFree(user.id!!)
                val projectedMinutes = ceil((usedSeconds + durationSeconds).toDouble() / 60.0).toInt()
                if (projectedMinutes > plan.maxAudioMinutesPerMonth) {
                    history.status = AudioProcessingStatus.ERROR
                    history.durationSeconds = durationSeconds
                    audioProcessingHistoryRepository.save(history)
                    throw WhisperLimitException(
                        "Este áudio ultrapassa o limite de ${plan.maxAudioMinutesPerMonth} min/mês do seu plano."
                    )
                }
            }
        }
        history.durationSeconds = durationSeconds
        history.status = newStatus
        audioProcessingHistoryRepository.save(history)
    }

    @Transactional
    fun updateUsageStatus(history: AudioProcessingHistory, newStatus: AudioProcessingStatus) {
        log.info("M=updateUsageStatus, I=Iniciando atualização de status de uso de áudio.")
        history.status = newStatus
        audioProcessingHistoryRepository.save(history)
    }

    fun countAudioEntriesCurrentMonthFree(userId: Long): Int {
        val (start, end) = currentMonthRange()
        return audioProcessingHistoryRepository.countByUserIdAndProcessedAtBetweenAndPaidWithCoinsFalse(userId, start, end)
    }

    fun usedAudioMinutesCurrentMonthFree(userId: Long): Int {
        val seconds = sumDurationSecondsCurrentMonthFree(userId)
        return ceil(seconds.toDouble() / 60.0).toInt()
    }

    fun sumDurationSecondsCurrentMonthFree(userId: Long): Long {
        val (start, end) = currentMonthRange()
        return audioProcessingHistoryRepository.sumDurationSecondsFreeInPeriod(userId, start, end)
    }

    private fun currentMonthRange(): Pair<LocalDateTime, LocalDateTime> {
        val now = LocalDateTime.now()
        val startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
        val endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59)
        return startOfMonth to endOfMonth
    }
}
