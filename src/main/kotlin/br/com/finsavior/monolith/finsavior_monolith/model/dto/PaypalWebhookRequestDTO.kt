package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.EventTypeEnum
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

data class PaypalWebhookRequestDTO(
    val id: String,
    @JsonProperty("create_time")
    val createTime: String? = null,
    @JsonProperty("event_type") @Enumerated(EnumType.STRING)
    val eventType: EventTypeEnum,
    val summary: String,
    val resource: ResourceDTO,
)
