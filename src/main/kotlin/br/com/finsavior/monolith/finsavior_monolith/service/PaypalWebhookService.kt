package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.assync.producer.WebhookProducer
import br.com.finsavior.monolith.finsavior_monolith.exception.PaymentException
import br.com.finsavior.monolith.finsavior_monolith.exception.WebhookException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ExternalUserDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PaypalEventTypeEnum.BILLING_SUBSCRIPTION_ACTIVATED
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PaypalEventTypeEnum.BILLING_SUBSCRIPTION_CANCELLED
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PaypalEventTypeEnum.BILLING_SUBSCRIPTION_EXPIRED
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PaypalEventTypeEnum.BILLING_SUBSCRIPTION_PAYMENT_FAILED
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PaypalEventTypeEnum.BILLING_SUBSCRIPTION_SUSPENDED
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toExternalUserDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.ExternalUserRepository
import br.com.finsavior.monolith.finsavior_monolith.service.strategy.WebhookService
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class PaypalWebhookService(
    private val webhookProducer: WebhookProducer,
    private val externalUserRepository: ExternalUserRepository,
    private val paymentService: PaymentService
) : WebhookService {

    private val log: KLogger = KotlinLogging.logger {}

    override fun sendMessage(webhookRequestDTO: WebhookRequestDTO, queueName: String) {
        try {
            webhookProducer.sendMessage(webhookRequestDTO, queueName)
        } catch (e: Exception) {
            log.error("Error while sending message for webhook event, error = ${e.message}")
            throw WebhookException("Error while sending message for webhook event, error = ${e.message}", e)
        }
    }

    override fun processWebhook(webhookRequestDTO: WebhookRequestDTO) {
        val externalUserdto: ExternalUserDTO
        try {
            externalUserdto = externalUserRepository.findBySubscriptionId(webhookRequestDTO.resource.id)!!.toExternalUserDTO()

            when (webhookRequestDTO.eventType) {
                BILLING_SUBSCRIPTION_ACTIVATED -> activatedEvent(externalUserdto, webhookRequestDTO)
                BILLING_SUBSCRIPTION_EXPIRED -> expiredEvent(externalUserdto)
                BILLING_SUBSCRIPTION_CANCELLED -> cancelledEvent(externalUserdto)
                BILLING_SUBSCRIPTION_PAYMENT_FAILED -> paymentFailedEvent(externalUserdto)
                BILLING_SUBSCRIPTION_SUSPENDED -> suspendedEvent(externalUserdto)
                else -> throw PaymentException("Evento não mapeado!")
            }
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    override fun suspendedEvent(externalUser: ExternalUserDTO) {
        try {
            downgradeUserPlan(externalUser)
        } catch (e: Exception) {
            throw PaymentException("Error ao atualizar plano do usuário:${externalUser.userId}", e)
        }
    }

    override fun paymentFailedEvent(externalUser: ExternalUserDTO) {
        try {
            downgradeUserPlan(externalUser)
        } catch (e: Exception) {
            throw PaymentException("Error ao atualizar plano do usuário:${externalUser.userId}")
        }
    }

    override fun cancelledEvent(externalUser: ExternalUserDTO) {
        try {
            downgradeUserPlan(externalUser)
        } catch (e: Exception) {
            throw PaymentException("Error ao atualizar plano do usuário:${externalUser.userId}")
        }
    }

    override fun expiredEvent(externalUser: ExternalUserDTO) {
        try {
            downgradeUserPlan(externalUser)
        } catch (e: Exception) {
            throw PaymentException("Error ao atualizar plano do usuário: ${externalUser.userId}")
        }
    }

    override fun activatedEvent(externalUser: ExternalUserDTO, webhookRequestDTO: WebhookRequestDTO) {
        try {
            upgradeUserPlan(externalUser, webhookRequestDTO)
        } catch (e: Exception) {
            throw PaymentException("Error ao atualizar plano do usuário: ${externalUser.userId}")
        }
    }

    override fun createdEvent(externalUser: ExternalUserDTO?, webhookRequestDTO: WebhookRequestDTO?) {
    }

    override fun downgradeUserPlan(externalUser: ExternalUserDTO) {
        if ((externalUser.planId == PlanTypeEnum.PLUS.id ||
                    externalUser.planId == PlanTypeEnum.PREMIUM.id)
        ) {
            externalUser.planId = PlanTypeEnum.FREE.id
            paymentService.updateUserPlan(externalUser)
        }
    }

    override fun upgradeUserPlan(externalUser: ExternalUserDTO, webhookRequestDTO: WebhookRequestDTO) {
        if (externalUser.planId == PlanTypeEnum.FREE.id) {
            externalUser.planId =
                PlanTypeEnum.fromValue(webhookRequestDTO.resource.planId).id
            paymentService.updateUserPlan(externalUser)
        }
    }
}