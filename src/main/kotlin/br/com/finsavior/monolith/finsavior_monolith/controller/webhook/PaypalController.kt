package br.com.finsavior.monolith.finsavior_monolith.controller.webhook

import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.service.strategy.WebhookService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhook")
class PaypalController(
    private val webhookService: WebhookService
) {

    companion object {
        const val PAYPAL_WEBHOOK_QUEUE = "br.com.finsavior.webhook.request"
    }

    @PostMapping("/subscription")
    fun webhookListener(@RequestBody webhookRequestDTO: WebhookRequestDTO) =
        webhookService.sendMessage(webhookRequestDTO, PAYPAL_WEBHOOK_QUEUE)
}