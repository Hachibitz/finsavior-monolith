package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.integration.dto.UpdateSubscriptionDTO
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
    fun createCheckoutSession(@RequestBody request: CheckoutSessionDTO): CheckoutSessionDTO =
        paymentService.createCheckoutSession(request.planType!!, request.email!!)

    @PostMapping("/subscription/cancel")
    @ResponseStatus(HttpStatus.OK)
    fun cancelSubscription(@RequestParam(defaultValue = "false") immediate: Boolean) =
        paymentService.cancelSubscription(immediate)

    @PostMapping("/subscription/update")
    fun updatePlan(@RequestBody request: UpdateSubscriptionDTO) =
        paymentService.updateSubscription(request.planType, request.email)

}