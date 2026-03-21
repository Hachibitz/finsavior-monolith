package br.com.finsavior.monolith.finsavior_monolith.controller.webhook

import br.com.finsavior.monolith.finsavior_monolith.config.coroutines.CustomProcessingScope
import br.com.finsavior.monolith.finsavior_monolith.service.TwilioService
import br.com.finsavior.monolith.finsavior_monolith.service.WhatsappService
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("whatsapp")
class WhatsappWebhookController(
    private val whatsappService: WhatsappService,
    private val processingScope: CustomProcessingScope,
    private val twilioService: TwilioService,
) {
    private val logger = KotlinLogging.logger {}

    @PostMapping(
        path = ["/webhook"],
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE]
    )
    fun handleWebhook(request: HttpServletRequest): ResponseEntity<Void> {
        if (!twilioService.validateRequest(request)) {
            logger.warn { "Invalid Twilio signature. Request ignored." }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        val payload = request.parameterMap
        val from = payload["From"]?.firstOrNull()?.substringAfter("whatsapp:")
        val messageSid = payload["MessageSid"]?.firstOrNull()
        val body = payload["Body"]?.firstOrNull()
        val numMedia = payload["NumMedia"]?.firstOrNull()?.toIntOrNull() ?: 0

        logger.info { "Received webhook request $messageSid from $from. NumMedia: $numMedia" }

        if (from != null) {
            processingScope.launch {
                if (numMedia > 0) {
                    val mediaUrl = payload["MediaUrl0"]?.firstOrNull()
                    if (mediaUrl != null) {
                        logger.info { "Processing audio message from $from at url: $mediaUrl" }
                        whatsappService.processIncomingAudio(from, mediaUrl)
                    }
                } else if (!body.isNullOrBlank()) {
                    logger.info { "Processing text message from $from: '$body'" }
                    whatsappService.processIncomingMessage(from, body)
                }
            }
        }
        return ResponseEntity.ok().build()
    }
}
