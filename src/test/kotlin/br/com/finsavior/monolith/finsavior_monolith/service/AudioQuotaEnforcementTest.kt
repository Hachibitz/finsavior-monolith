package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperLimitException
import br.com.finsavior.monolith.finsavior_monolith.model.entity.AudioProcessingHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Plan
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserPlan
import br.com.finsavior.monolith.finsavior_monolith.model.enums.AudioProcessingStatus
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.AudioProcessingHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class AudioQuotaEnforcementTest {
    @Test
    fun `rejects audio exceeding monthly minute cap before calling OpenAI`() {
        val historyRepo = mock<AudioProcessingHistoryRepository>()
        val userService = mock<UserService>()
        val userRepo = mock<UserRepository>()

        val user = User(username = "u", email = "u@test.com", password = "x", firstName = "U", lastName = "T")
            .copy(id = 42L)
        user.userPlan = UserPlan(
            userId = 42L,
            plan = Plan(id = PlanTypeEnum.FREE.id, description = PlanTypeEnum.FREE)
        )

        whenever(userRepo.findById(42L)).thenReturn(Optional.of(user))
        whenever(
            historyRepo.countByUserIdAndProcessedAtBetweenAndPaidWithCoinsFalseAndStatusNot(
                eq(42L), any(), any(), eq(AudioProcessingStatus.ERROR)
            )
        ).thenReturn(0)
        // FREE cap is 5 minutes (300s). Already used 240s; a 120s clip projects to 6 minutes -> must reject.
        whenever(historyRepo.sumDurationSecondsFreeInPeriod(eq(42L), any(), any())).thenReturn(240L)

        val service = AudioProcessingHistoryService(historyRepo, userService, userRepo)

        assertThrows(WhisperLimitException::class.java) {
            service.reserveAudioUsage(isUsingCoins = false, durationSeconds = 120, explicitUserId = 42L)
        }
        verify(historyRepo, never()).save(any<AudioProcessingHistory>())
    }

    @Test
    fun `reserves pending duration when within monthly limit`() {
        val historyRepo = mock<AudioProcessingHistoryRepository>()
        val userService = mock<UserService>()
        val userRepo = mock<UserRepository>()

        val user = User(username = "u", email = "u@test.com", password = "x", firstName = "U", lastName = "T")
            .copy(id = 42L)
        user.userPlan = UserPlan(
            userId = 42L,
            plan = Plan(id = PlanTypeEnum.FREE.id, description = PlanTypeEnum.FREE)
        )

        whenever(userRepo.findById(42L)).thenReturn(Optional.of(user))
        whenever(
            historyRepo.countByUserIdAndProcessedAtBetweenAndPaidWithCoinsFalseAndStatusNot(
                eq(42L), any(), any(), eq(AudioProcessingStatus.ERROR)
            )
        ).thenReturn(0)
        whenever(historyRepo.sumDurationSecondsFreeInPeriod(eq(42L), any(), any())).thenReturn(120L)
        whenever(historyRepo.save(any<AudioProcessingHistory>())).thenAnswer { it.getArgument<AudioProcessingHistory>(0) }

        val service = AudioProcessingHistoryService(historyRepo, userService, userRepo)
        val reserved = service.reserveAudioUsage(isUsingCoins = false, durationSeconds = 90, explicitUserId = 42L)

        assertEquals(AudioProcessingStatus.PENDING, reserved.status)
        assertEquals(90, reserved.durationSeconds)
    }
}
