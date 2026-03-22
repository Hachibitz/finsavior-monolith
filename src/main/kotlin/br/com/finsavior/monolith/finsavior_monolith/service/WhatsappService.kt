package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import br.com.finsavior.monolith.finsavior_monolith.security.UserSecurityDetails
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.util.regex.Pattern

@Service
class WhatsappService(
    private val userRepository: UserRepository,
    private val twilioService: TwilioService,
    private val aiTranscriptionService: AiTranscriptionService,
    private val aiChatService: AiChatService,
    private val userSecurityDetails: UserSecurityDetails
) {
    private val logger = KotlinLogging.logger {}

    private val markdownImageRegex: Pattern = Pattern.compile("!\\[.*?\\]\\((https?://[^\\s)]+)\\)")

    companion object {
        private const val TWILIO_MESSAGE_LIMIT = 1580 // Limite do WhatsApp Twilio é 1600
    }

    suspend fun processIncomingMessage(from: String, body: String) {
        val user = validateUser(from) ?: return

        try {
            authenticateUserInContext(user)

            val request = AiChatRequest(question = body, isUsingCoins = false)
            val responseEntity = aiChatService.chatWithAssistant(request)
            val aiResponse = responseEntity.body?.answer ?: "Desculpe, não consegui processar sua solicitação."

            processAndSendAiResponse(from, aiResponse)
        } catch (e: Exception) {
            logger.error(e) { "Error processing text message from $from" }
            twilioService.sendMessage(from, "Desculpe, ocorreu um erro ao processar sua mensagem. Tente novamente mais tarde.")
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    suspend fun processIncomingAudio(from: String, mediaUrl: String) {
        val user = validateUser(from) ?: return

        try {
            val audioFile = twilioService.downloadMedia(mediaUrl)
            val transcribedText = aiTranscriptionService.transcribeAudioFromFile(audioFile)

            if (transcribedText.isBlank()) {
                logger.warn { "Transcription result for audio from $from is blank." }
                twilioService.sendMessage(from, "Desculpe, não consegui identificar o que você disse no áudio. Pode tentar novamente?")
                return
            }

            logger.info { "Audio from $from transcribed to: '$transcribedText'" }
            processIncomingMessage(from, transcribedText)

        } catch (e: Exception) {
            logger.error(e) { "Error processing audio from $from" }
            twilioService.sendMessage(from, "Desculpe, ocorreu um erro ao processar seu áudio. Tente novamente mais tarde.")
        }
    }

    private fun authenticateUserInContext(user: User) {
        val userDetails = userSecurityDetails.loadUserByUsername(user.username)
        val authentication = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    private fun findUserByPhoneNumber(from: String): User? {
        val user = userRepository.findByPhoneNumber(from)
        if (user != null) return user

        if (from.startsWith("+55")) {
            return resolveBrazilianPhoneNumber(from)
        }

        return null
    }

    private fun resolveBrazilianPhoneNumber(from: String): User? {
        return if (from.length == 13) {
            // Número sem o 9 (Ex: +55 84 9999999). Tenta buscar na base COM o 9.
            val ddd = from.substring(0, 5) // "+5584"
            val number = from.substring(5) // "8398888"
            userRepository.findByPhoneNumber("${ddd}9$number")
        } else if (from.length == 14) {
            // Número com o 9 (Ex: +55 84 99999999). Tenta buscar na base SEM o 9.
            val ddd = from.substring(0, 5) // "+5584"
            val number = from.substring(6) // Pula o 9
            userRepository.findByPhoneNumber("$ddd$number")
        } else {
            null
        }
    }

    private fun validateUser(from: String): User? {
        val user = findUserByPhoneNumber(from)

        if (user == null) {
            logger.warn { "Received message from unknown number: $from" }
            twilioService.sendMessage(from, "Desculpe, seu número não está cadastrado. Por favor, adicione seu número no app FinSavior para usar esta funcionalidade.")
            return null
        }

        if (user.userProfile?.isWhatsappEnabled == false) {
            logger.warn { "Whatsapp feature is disabled for user ${user.id}" }
            twilioService.sendMessage(from, "A funcionalidade do WhatsApp está desabilitada. Você pode ativá-la nas configurações do seu perfil no app.")
            return null
        }

        val planType = user.userPlan?.plan?.id?.let { CommonUtils.getPlanTypeById(it) } ?: PlanTypeEnum.FREE
        if (planType == PlanTypeEnum.FREE) {
            logger.warn { "User ${user.id} is on a FREE plan and cannot use the Whatsapp feature." }
            twilioService.sendMessage(from, "Esta é uma funcionalidade para usuários assinantes. Por favor, considere fazer um upgrade para um de nossos planos pagos.")
            return null
        }

        return user
    }

    private suspend fun processAndSendAiResponse(to: String, response: String) {
        logger.info("Processing AI response for $to")
        val messageQueue = mutableListOf<Pair<String, String>>()

        val matcher = markdownImageRegex.matcher(response)
        var lastIndex = 0

        while (matcher.find()) {
            val textPart = response.substring(lastIndex, matcher.start()).trim()
            if (textPart.isNotBlank()) {
                messageQueue.add(Pair("TEXTO", textPart))
            }

            val imageUrl = matcher.group(1)
            messageQueue.add(Pair("FOTO", imageUrl))

            lastIndex = matcher.end()
        }

        if (lastIndex < response.length) {
            val remainingText = response.substring(lastIndex).trim()
            if (remainingText.isNotBlank()) {
                messageQueue.add(Pair("TEXTO", remainingText))
            }
        }

        messageQueue.forEach { (type, content) ->
            if (type == "TEXTO") {
                val cleanText = content.replace(Regex("[*_]"), "")
                val messageParts = splitMessage(cleanText, TWILIO_MESSAGE_LIMIT)
                messageParts.forEachIndexed { index, part ->
                    twilioService.sendMessage(to, part)
                    if (index < messageParts.size - 1) {
                        delay(1200L)
                    }
                }
            } else {
                twilioService.sendMediaMessage(to, content)
                delay(3000L)
            }
            delay(1500L)
        }
    }

    private fun splitMessage(text: String, maxLength: Int): List<String> {
        if (text.length <= maxLength) {
            return listOf(text)
        }

        val parts = mutableListOf<String>()
        var remainingText = text

        while (remainingText.isNotEmpty()) {
            if (remainingText.length <= maxLength) {
                parts.add(remainingText)
                break
            }

            val chunk = remainingText.substring(0, maxLength)
            val splitPosition = chunk.lastIndexOf("\n\n")
                .takeIf { it != -1 } ?: chunk.lastIndexOf("\n")
                .takeIf { it != -1 } ?: chunk.lastIndexOf(" ")
                .takeIf { it != -1 } ?: maxLength

            val part = remainingText.substring(0, splitPosition).trim()
            if (part.isNotEmpty()) {
                parts.add(part)
            }
            remainingText = remainingText.substring(splitPosition).trim()
        }

        return parts
    }
}
