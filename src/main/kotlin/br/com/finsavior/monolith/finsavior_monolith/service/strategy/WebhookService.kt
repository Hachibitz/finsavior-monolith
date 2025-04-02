package br.com.finsavior.monolith.finsavior_monolith.service.strategy

import br.com.finsavior.monolith.finsavior_monolith.model.dto.ExternalUserDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import org.springframework.stereotype.Service

@Service
interface WebhookService {
    fun sendMessage(webhookRequestDTO: WebhookRequestDTO, queueName: String)
    fun upgradeUserPlan(externalUser: ExternalUserDTO, webhookRequestDTO: WebhookRequestDTO)
    fun downgradeUserPlan(externalUser: ExternalUserDTO)
    fun activatedEvent(externalUser: ExternalUserDTO, webhookRequestDTO: WebhookRequestDTO)
    fun createdEvent(externalUser: ExternalUserDTO?, webhookRequestDTO: WebhookRequestDTO?)
    fun expiredEvent(externalUser: ExternalUserDTO)
    fun cancelledEvent(externalUser: ExternalUserDTO)
    fun paymentFailedEvent(externalUser: ExternalUserDTO)
    fun suspendedEvent(externalUser: ExternalUserDTO)
    fun processWebhook(webhookRequestDTO: WebhookRequestDTO)
}