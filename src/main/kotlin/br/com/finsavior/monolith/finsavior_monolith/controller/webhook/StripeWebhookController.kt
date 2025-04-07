package br.com.finsavior.monolith.finsavior_monolith.controller.webhook

import br.com.finsavior.monolith.finsavior_monolith.service.StripeWebhookService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/stripe/payment")
class StripeWebhookController(
    private val stripeWebhookService: StripeWebhookService
) {

    @PostMapping("/webhook")
    fun handleStripeWebhook(
        request: HttpServletRequest,
        @RequestBody payload: String,
        @RequestHeader("Stripe-Signature") sigHeader: String
    ): ResponseEntity<String> =
        stripeWebhookService.processWebhookStart(payload, sigHeader)
}
