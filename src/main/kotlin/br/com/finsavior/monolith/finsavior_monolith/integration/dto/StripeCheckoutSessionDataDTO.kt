package br.com.finsavior.monolith.finsavior_monolith.integration.dto

import com.stripe.model.Quote.Computed.Recurring.TotalDetails
import com.stripe.model.checkout.Session.PaymentMethodConfigurationDetails
import com.stripe.model.checkout.Session.PaymentMethodOptions
import com.stripe.model.checkout.Session.PhoneNumberCollection
import com.fasterxml.jackson.annotation.JsonProperty

data class StripeCheckoutSessionDataDTO (
    @JsonProperty("object")
    val session: StripeCheckoutSessionDTO
)