package br.com.finsavior.monolith.finsavior_monolith.integration.client

import br.com.finsavior.monolith.finsavior_monolith.integration.client.config.StripeClientConfig
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripeCustomerResponse
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripePlanListResponse
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripePriceResponse
import br.com.finsavior.monolith.finsavior_monolith.integration.dto.StripeSubscriptionResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@FeignClient(
    name = "stripeClient",
    url = "https://api.stripe.com/v1",
    configuration = [StripeClientConfig::class]
)
interface StripeClient {

    @GetMapping("/prices")
    fun getPricesByProduct(
        @RequestParam("product") productId: String
    ): StripePriceResponse

    @GetMapping("/subscriptions/{subscriptionId}")
    fun getSubscription(
        @PathVariable("subscriptionId") subscriptionId: String
    ): StripeSubscriptionResponse

    @PostMapping("/subscriptions/{subscriptionId}")
    fun cancelAtPeriodEnd(
        @PathVariable subscriptionId: String,
        @RequestParam("cancel_at_period_end") cancelAtPeriodEnd: Boolean = true
    )

    @DeleteMapping("/subscriptions/{subscriptionId}")
    fun deleteSubscriptionImmediately(
        @PathVariable subscriptionId: String
    )

    @PostMapping("/subscriptions/{subscriptionId}")
    fun updateSubscriptionPrice(
        @PathVariable("subscriptionId") subscriptionId: String,
        @RequestParam("items[0][id]") subscriptionItemId: String,
        @RequestParam("items[0][price]") newPriceId: String
    )

    @GetMapping("/customers/{customerId}")
    fun getCustomer(
        @PathVariable("customerId") customerId: String
    ): StripeCustomerResponse

    @GetMapping("/plans")
    fun listPlans(
        @RequestParam("product", required = false) productId: String? = null,
        @RequestParam("active", required = false) active: Boolean? = null,
        @RequestParam("limit", required = false) limit: Int? = null
    ): StripePlanListResponse
}
