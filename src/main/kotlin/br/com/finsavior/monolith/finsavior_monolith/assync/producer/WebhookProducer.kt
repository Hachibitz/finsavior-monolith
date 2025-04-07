package br.com.finsavior.monolith.finsavior_monolith.assync.producer

import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import mu.KLogger
import mu.KotlinLogging
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Component

@Component
class WebhookProducer(
    private val rabbitTemplate: RabbitTemplate
) {

    companion object {
        const val WEBHOOK_QUEUE = "br.com.finsavior.webhook-request"
    }

    private val log: KLogger = KotlinLogging.logger {}

    fun sendMessage(message: WebhookRequestDTO) {
        log.info("Sending message to: $WEBHOOK_QUEUE")
        rabbitTemplate.convertAndSend(
            "", // exchange default
            WEBHOOK_QUEUE,
            message
        )
    }
}