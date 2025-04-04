package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.SubscriptionDTO
import br.com.finsavior.monolith.finsavior_monolith.service.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("payment")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping("/create-subscription")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSubscription(@RequestBody subscription: SubscriptionDTO) =
        paymentService.createSubscription(subscription)
}