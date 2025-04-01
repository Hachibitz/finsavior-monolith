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

    private val log: KLogger = KotlinLogging.logger {}

    fun sendMessage(message: WebhookRequestDTO, queueName: String) {
        log.info("Sending message to: $queueName")
        rabbitTemplate.convertAndSend(
            "", // exchange default
            queueName,
            message
        )
    }
}