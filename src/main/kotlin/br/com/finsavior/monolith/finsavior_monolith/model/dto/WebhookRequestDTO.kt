package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.EventTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum

data class WebhookRequestDTO(
    val id: String? = null,
    val createTime: String? = null,
    val eventType: EventTypeEnum,
    val summary: String? = null,
    val resource: ResourceDTO? = null,
    var planType: PlanTypeEnum? = null,
    var userId: Long? = null,
    var email: String? = null,
    var externalUserId: String? = null,
    var subscriptionId: String? = null,
    var rawPayload: String? = null
)

