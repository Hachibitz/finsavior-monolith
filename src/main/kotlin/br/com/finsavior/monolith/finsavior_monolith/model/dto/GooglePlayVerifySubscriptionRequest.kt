package br.com.finsavior.monolith.finsavior_monolith.model.dto

import jakarta.validation.constraints.NotBlank

data class GooglePlayVerifySubscriptionRequest(
    @field:NotBlank val productId: String,
    @field:NotBlank val purchaseToken: String,
    val packageName: String? = null
)

data class GooglePlayVerifySubscriptionResponse(
    val planType: String,
    val subscriptionStatus: String,
    val provider: String = "GOOGLE_PLAY"
)

data class GooglePlayProductCatalogResponse(
    val products: Map<String, PlayBillingSkuDto>
)

data class PlayBillingSkuDto(
    val productId: String,
    val basePlanId: String
)

data class GooglePlayRtdnMessage(
    val message: RtdnPubSubMessage? = null,
    val subscription: String? = null
)

data class RtdnPubSubMessage(
    val data: String? = null,
    val messageId: String? = null
)
