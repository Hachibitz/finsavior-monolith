package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.PaymentException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.SubscriptionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ExternalUser
import br.com.finsavior.monolith.finsavior_monolith.model.entity.PlanChangeHistory
import br.com.finsavior.monolith.finsavior_monolith.model.enums.ExternalProvider
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.ExternalUserRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanHistoryRepository
import mu.KLogger
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class PaymentService(
    @Lazy private val userService: UserService,
    private val externalUserRepository: ExternalUserRepository,
    private val planHistoryRepository: PlanHistoryRepository
) {

    val log: KLogger = KotlinLogging.logger {}

    @Transactional
    fun createSubscription(request: SubscriptionDTO) {
        try {
            val userId: Long = userService.getUserByContext().id!!
            saveExternalUser(request, userId)
            updatePlanHistory(request, userId)
        } catch (e: Exception) {
            log.error("Falha ao criar a subscrição: ${e.message}")
            throw PaymentException(e.message, e)
        }
    }

    private fun saveExternalUser(request: SubscriptionDTO, userId: Long) {
        val externalProvider: ExternalProvider? = Arrays.stream(ExternalProvider.entries.toTypedArray())
            .filter { provider -> provider.name == ExternalProvider.PAYPAL.name }
            .findFirst()
            .orElseThrow { IllegalArgumentException("Invalid external provider") }

        val externalUser: ExternalUser = ExternalUser(
            subscriptionId = request.subscriptionId,
            externalUserId = request.externalUserId,
            externalUserEmail = request.payer.externalEmailAddress,
            externalProvider = ExternalProvider.PAYPAL,
            userId = userId,
            audit = Audit()
        )

        externalUserRepository.save(externalUser)
    }

    private fun updatePlanHistory(request: SubscriptionDTO, userId: Long) {
        val planType: PlanTypeEnum? = getPlanTypeById(request.planId!!)

        val planChangeHistory = PlanChangeHistory(
            userId = userId,
            externalUserId = request.externalUserId,
            planId = request.planId,
            planType = planType!!,
            updateTime = LocalDateTime.now(),
            audit = Audit()
        )

        planHistoryRepository.save(planChangeHistory)
    }

    private fun getPlanTypeById(planTypeId: String): PlanTypeEnum? =
        Arrays.stream(PlanTypeEnum.entries.toTypedArray())
            .filter { planType -> planType.id == planTypeId }
            .findFirst()
            .orElse(null)
}