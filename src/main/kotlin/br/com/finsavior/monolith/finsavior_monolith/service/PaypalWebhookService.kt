package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.assync.producer.WebhookProducer
import br.com.finsavior.monolith.finsavior_monolith.exception.WebhookException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.service.strategy.WebhookService
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service

@Service
class PaypalWebhookService(
    private val webhookProducer: WebhookProducer
) : WebhookService {

    private val log: KLogger = KotlinLogging.logger {}

    override fun sendMessage(webhookRequestDTO: WebhookRequestDTO, queueName: String) {
        try {
            webhookProducer.sendMessage(webhookRequestDTO, queueName)
        } catch (e: Exception) {
            log.error("Error while sending message for webhook event, error = ${e.message}")
            throw WebhookException("Error while sending message for webhook event, error = ${e.message}", e)
        }
    }
}