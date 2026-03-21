package br.com.finsavior.monolith.finsavior_monolith.service

import com.twilio.Twilio
import com.twilio.rest.api.v2010.account.Message
import com.twilio.security.RequestValidator
import com.twilio.type.PhoneNumber
import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import mu.KLogger
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.io.File
import java.io.FileOutputStream
import java.net.URI

@Service
class TwilioService(
    @param:Value("\${twilio.whatsapp-number}") private val fromNumber: String,
    @param:Value("\${twilio.account-sid}") private val twilioAccountSid: String,
    @param:Value("\${twilio.auth-token}") private val twilioAuthToken: String,
    @param:Value("\${finsavior.active-profile}") private val activeProfile: String,
    private val restTemplate: RestTemplate,
) {
    private val logger: KLogger = KotlinLogging.logger {}
    private val validator = RequestValidator(twilioAuthToken.trim())

    @PostConstruct
    fun initTwilio() {
        Twilio.init(twilioAccountSid.trim(), twilioAuthToken.trim())
        logger.info { "Twilio SDK initialized successfully." }
    }

    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    fun sendMessage(to: String, body: String) {
        executeMessageCreation(to) {
            Message.creator(formatWhatsappNumber(to), formatWhatsappNumber(fromNumber), body).create()
            logger.info("Message sent to $to")
        }
    }

    @Retryable(
        retryFor = [Exception::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000, multiplier = 2.0)
    )
    fun sendMediaMessage(to: String, mediaUrl: String) {
        executeMessageCreation(to) {
            Message.creator(
                formatWhatsappNumber(to),
                formatWhatsappNumber(fromNumber),
                listOf(URI.create(mediaUrl))
            ).create()
            logger.info("Media message sent to $to, url: $mediaUrl")
        }
    }

    private fun executeMessageCreation(to: String, action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            logger.error("Failed to send message to $to. Error: ${e.message}", e)
            throw e
        }
    }

    private fun formatWhatsappNumber(number: String): PhoneNumber {
        val cleanNumber = number.trim()
        val formatted = if (cleanNumber.startsWith("whatsapp:")) cleanNumber else "whatsapp:$cleanNumber"
        return PhoneNumber(formatted)
    }

    fun downloadMedia(mediaUrl: String): File {
        val tempFile = File.createTempFile("twilio_media_", ".tmp")
        val response = restTemplate.getForEntity(mediaUrl, ByteArray::class.java)

        if (response.statusCode.is2xxSuccessful) {
            FileOutputStream(tempFile).use { fos ->
                fos.write(response.body)
            }
            return tempFile
        } else {
            throw RuntimeException("Failed to download media from Twilio. Status: ${response.statusCode}")
        }
    }

    fun validateRequest(request: HttpServletRequest): Boolean {
        if (activeProfile.contains("dev") || activeProfile.contains("local")) {
            logger.info { "Dev profile active: Bypassing Twilio Signature Validation." }
            return true
        }

        val signature = request.getHeader("X-Twilio-Signature")

        if (signature.isNullOrBlank()) {
            logger.warn { "Missing X-Twilio-Signature header" }
            return false
        }

        var url = request.requestURL.toString()
        val forwardedProto = request.getHeader("X-Forwarded-Proto")

        if (forwardedProto == "https" || url.contains("pinggy")) {
            url = url.replaceFirst("http://", "https://")
        }

        val queryString = request.queryString
        if (!queryString.isNullOrBlank()) {
            url = "$url?$queryString"
        }

        val params: Map<String, String> = request.parameterMap.entries.associate {
            it.key to (it.value.firstOrNull() ?: "")
        }

        logger.info { "Validating Twilio request. URL: '$url', Signature: '$signature', Params map size: ${params.size}" }

        return validator.validate(url, params, signature)
    }
}
