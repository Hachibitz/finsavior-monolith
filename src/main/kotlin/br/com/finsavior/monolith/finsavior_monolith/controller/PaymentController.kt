package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.integration.dto.UpdateSubscriptionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CancelSubscriptionRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CheckoutSessionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.SubscriptionDTO
import br.com.finsavior.monolith.finsavior_monolith.service.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("payment")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping("/subscription/create-subscription")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSubscription(@RequestBody subscription: SubscriptionDTO) =
        paymentService.createSubscription(subscription)

    @PostMapping("/subscription/create-checkout")
    @ResponseStatus(HttpStatus.CREATED)
    fun createCheckoutSession(@RequestBody request: CheckoutSessionDTO): CheckoutSessionDTO =
        paymentService.createCheckoutSession(request.planType!!, request.email!!)

    @PostMapping("/subscription/cancel")
    @ResponseStatus(HttpStatus.OK)
    fun cancelSubscription(@RequestBody request: CancelSubscriptionRequest) =
        paymentService.cancelSubscription(request.immediate)

    @PostMapping("/subscription/update")
    @ResponseStatus(HttpStatus.CREATED)
    fun updatePlan(@RequestBody request: UpdateSubscriptionDTO) =
        paymentService.updateSubscription(request.planType, request.email)

    @PostMapping("/subscription/customer-portal")
    @ResponseStatus(HttpStatus.OK)
    fun createCustomerPortalSession(@RequestParam email: String): Map<String, String> =
        paymentService.createCustomerPortalSession(email)

}