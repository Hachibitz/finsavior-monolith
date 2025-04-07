package br.com.finsavior.monolith.finsavior_monolith.model.enums

enum class EventTypeEnum(val value: String, val provider: ExternalProvider) {

    // ### PAYPAL ###
    BILLING_SUBSCRIPTION_ACTIVATED("BILLING.SUBSCRIPTION.ACTIVATED", ExternalProvider.PAYPAL),
    BILLING_SUBSCRIPTION_CANCELLED("BILLING.SUBSCRIPTION.CANCELLED", ExternalProvider.PAYPAL),
    BILLING_SUBSCRIPTION_CREATED("BILLING.SUBSCRIPTION.CREATED", ExternalProvider.PAYPAL),
    BILLING_SUBSCRIPTION_EXPIRED("BILLING.SUBSCRIPTION.EXPIRED", ExternalProvider.PAYPAL),
    BILLING_SUBSCRIPTION_PAYMENT_FAILED("BILLING.SUBSCRIPTION.PAYMENT.FAILED", ExternalProvider.PAYPAL),
    BILLING_SUBSCRIPTION_SUSPENDED("BILLING.SUBSCRIPTION.SUSPENDED", ExternalProvider.PAYPAL),

    // ### STRIPE ###
    CHECKOUT_SESSION_COMPLETED("checkout.session.completed", ExternalProvider.STRIPE),
    CUSTOMER_SUBSCRIPTION_DELETED("customer.subscription.deleted", ExternalProvider.STRIPE),
    INVOICE_PAYMENT_FAILED("invoice.payment_failed", ExternalProvider.STRIPE),
    CUSTOMER_SUBSCRIPTION_UPDATED("customer.subscription.updated", ExternalProvider.STRIPE);

    companion object {
        fun fromValue(value: String): EventTypeEnum {
            return EventTypeEnum.entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown event type: $value")
        }

        fun fromProvider(provider: ExternalProvider): List<EventTypeEnum> {
            return EventTypeEnum.entries.filter { it.provider == provider }
        }
    }
}