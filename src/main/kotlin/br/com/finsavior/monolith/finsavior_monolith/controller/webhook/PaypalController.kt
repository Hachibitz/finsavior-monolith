package br.com.finsavior.monolith.finsavior_monolith.controller.webhook

import br.com.finsavior.monolith.finsavior_monolith.model.dto.PaypalWebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toWebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.service.PaypalWebhookService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/webhook")
class PaypalController(
    private val paypalWebhookService: PaypalWebhookService
) {

    @PostMapping("/subscription")
    fun webhookListener(@RequestBody paypalWebhookRequestDTO: PaypalWebhookRequestDTO) {
        paypalWebhookService.sendMessage(paypalWebhookRequestDTO.toWebhookRequestDTO())
    }
}