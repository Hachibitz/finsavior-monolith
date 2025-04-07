package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripeCheckoutSessionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.PaypalWebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ExternalUser
import br.com.finsavior.monolith.finsavior_monolith.model.enums.EventTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import java.time.LocalDateTime

fun PaypalWebhookRequestDTO.toWebhookRequestDTO(): WebhookRequestDTO = 
    WebhookRequestDTO(
        id = this.id,
        eventType = this.eventType,
        resource = this.resource,
        createTime = this.createTime,
        summary = this.summary,
    )

fun StripeCheckoutSessionDTO.toWebhookRequestDTO(
    eventType: String,
    planTypeEnum: PlanTypeEnum,
    subscriptionId: String,
    userId: Long?
): WebhookRequestDTO =
    WebhookRequestDTO(
        eventType = EventTypeEnum.fromValue(eventType),
        createTime = LocalDateTime.now().toString(),
        planType = planTypeEnum,
        email = this.customerDetails.email,
        externalUserId = this.customerId,
        subscriptionId = subscriptionId,
        userId = userId
    )

fun WebhookRequestDTO.toExternalUser(): ExternalUser =
    ExternalUser(
        userId = this.userId!!,
        subscriptionId = this.subscriptionId,
        externalUserId = this.externalUserId,
        externalUserEmail = this.email,
        externalProvider = this.eventType.provider,
        audit = Audit(),
    )