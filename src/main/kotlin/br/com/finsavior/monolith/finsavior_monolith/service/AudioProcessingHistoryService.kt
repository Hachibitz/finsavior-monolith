package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperLimitException
import br.com.finsavior.monolith.finsavior_monolith.model.entity.AudioProcessingHistory
import br.com.finsavior.monolith.finsavior_monolith.model.enums.AudioProcessingStatus
import br.com.finsavior.monolith.finsavior_monolith.repository.AudioProcessingHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.ceil

@Service
class AudioProcessingHistoryService(
    private val audioProcessingHistoryRepository: AudioProcessingHistoryRepository,
    private val userService: UserService,
    private val userRepository: UserRepository
) {

    private val log: KLogger = KotlinLogging.logger {  }
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

    @Transactional
    fun reserveAudioUsage(isUsingCoins: Boolean, durationSeconds: Int = 0, explicitUserId: Long? = null): AudioProcessingHistory {
        val user = if (explicitUserId != null) {
            userRepository.findById(explicitUserId).orElseThrow {
                WhisperLimitException("Usuário não encontrado para validação de áudio.")
            }
        } else {
            userService.getUserByContext()
        }
        val userId = user.id!!
        val lock = userLocks.computeIfAbsent(userId) { ReentrantLock() }

        return lock.withLock {
            val plan = getPlanTypeById(user.userPlan!!.plan.id)
                ?: throw WhisperLimitException("Plano não encontrado para validação de áudio.")

            if (!isUsingCoins) {
                val usageCount = countAudioEntriesCurrentMonthFree(userId)
                if (usageCount >= plan.maxAudioBillEntries && plan.maxAudioBillEntries < Int.MAX_VALUE) {
                    throw WhisperLimitException(
                        "Limite mensal de áudios atingido (${plan.maxAudioBillEntries}/mês). Faça upgrade ou use FSCoins!"
                    )
                }

                val usedSeconds = sumDurationSecondsCurrentMonthFree(userId)
                val projectedMinutes = ceil((usedSeconds + durationSeconds).toDouble() / 60.0).toInt()
                if (plan.maxAudioMinutesPerMonth < Int.MAX_VALUE && projectedMinutes > plan.maxAudioMinutesPerMonth) {
                    throw WhisperLimitException(
                        "Este áudio ultrapassa o limite de ${plan.maxAudioMinutesPerMonth} min/mês do seu plano. Faça upgrade ou use FSCoins!"
                    )
                }
            }

            val history = AudioProcessingHistory(
                userId = userId,
                status = AudioProcessingStatus.PENDING,
                paidWithCoins = isUsingCoins,
                durationSeconds = durationSeconds.takeIf { it > 0 }
            )
            audioProcessingHistoryRepository.save(history)
        }
    }

    @Transactional
    fun completeAudioUsage(history: AudioProcessingHistory, durationSeconds: Int, newStatus: AudioProcessingStatus) {
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
        return audioProcessingHistoryRepository.countByUserIdAndProcessedAtBetweenAndPaidWithCoinsFalseAndStatusNot(
            userId,
            start,
            end,
            AudioProcessingStatus.ERROR
        )
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
