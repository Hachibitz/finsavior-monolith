package br.com.finsavior.monolith.finsavior_monolith.service.strategy

import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import org.springframework.stereotype.Service

@Service
interface WebhookService {
    fun sendMessage(webhookRequestDTO: WebhookRequestDTO)
    fun processWebhook(webhookRequestDTO: WebhookRequestDTO)
}