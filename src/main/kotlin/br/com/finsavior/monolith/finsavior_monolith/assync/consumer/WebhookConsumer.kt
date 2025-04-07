package br.com.finsavior.monolith.finsavior_monolith.assync.consumer

import br.com.finsavior.monolith.finsavior_monolith.model.dto.WebhookRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.enums.ExternalProvider
import br.com.finsavior.monolith.finsavior_monolith.service.PaypalWebhookService
import br.com.finsavior.monolith.finsavior_monolith.service.StripeWebhookService
import mu.KLogger
import mu.KotlinLogging
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Service

@Service
class WebhookConsumer(
    private val paypalWebhookService: PaypalWebhookService,
    private val stripeWebhookService: StripeWebhookService
) {

    companion object {
        const val WEBHOOK_REQUEST_QUEUE = "br.com.finsavior.webhook-request"
    }

    private val log: KLogger = KotlinLogging.logger {}

    @RabbitListener(queues = [WEBHOOK_REQUEST_QUEUE])
    fun listen(message: WebhookRequestDTO) {
        log.info("Message consumed: $message")
        if(message.eventType.provider == ExternalProvider.STRIPE) {
            log.info("Processing Stripe webhook")
            stripeWebhookService.processWebhook(message)
        } else if(message.eventType.provider == ExternalProvider.PAYPAL) {
            log.info("Processing Paypal webhook")
            paypalWebhookService.processWebhook(message)
        } else {
            log.error("Unknown provider: ${message.eventType.provider}")
            throw IllegalArgumentException("Unknown provider: ${message.eventType.provider}")
        }
    }
}