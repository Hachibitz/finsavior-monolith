package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.WhatsappIntegrationException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.entity.WhatsappMessageHistory
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.WhatsappMessageHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.security.UserSecurityDetails
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils
import kotlinx.coroutines.delay
import mu.KotlinLogging
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class WhatsappSilentException : RuntimeException("Silently ignored")

@Service
class WhatsappService(
    private val userRepository: UserRepository,
    private val twilioService: TwilioService,
    private val aiTranscriptionService: AiTranscriptionService,
    private val aiChatService: AiChatService,
    private val userSecurityDetails: UserSecurityDetails,
    private val whatsappMessageHistoryRepository: WhatsappMessageHistoryRepository
) {
    private val logger = KotlinLogging.logger {}

    private val markdownImageRegex: Pattern = Pattern.compile("!\\[.*?\\]\\((https?://[^\\s)]+)\\)")
    
    // Armazena a última vez que o usuário foi avisado sobre a cota excedida
    private val lastWarningSentMap = ConcurrentHashMap<Long, LocalDateTime>()

    companion object {
        private const val TWILIO_MESSAGE_LIMIT = 1580 // Limite do WhatsApp Twilio é 1600
    }

    suspend fun processIncomingMessage(from: String, body: String) {
        val user = validateUser(from) ?: return

        try {
            validateWhatsappQuota(user)
            whatsappMessageHistoryRepository.save(WhatsappMessageHistory(userId = user.id!!))
            
            // Verifica se é a primeira interação da vida do usuário no WhatsApp
            val isFirstInteraction = whatsappMessageHistoryRepository.countByUserId(user.id!!) == 1L
            if (isFirstInteraction) {
                sendWelcomeMessage(from)
                delay(1500L) // Pausa dramática e natural antes da IA responder ao comando real
            }

            // Autentica o usuário no contexto atual para que as MCP Tools funcionem corretamente
            authenticateUserInContext(user)

            val request = AiChatRequest(question = body, isUsingCoins = false)
            val responseEntity = aiChatService.chatWithAssistant(request)
            val aiResponse = responseEntity.body?.answer ?: "Desculpe, não consegui processar sua solicitação."

            processAndSendAiResponse(from, aiResponse)
        } catch (e: WhatsappSilentException) {
            logger.warn { "WhatsApp limit triggered for user ${user.id}, but warning was already sent recently. Silently dropping." }
        } catch (e: WhatsappIntegrationException) {
            logger.warn { "WhatsApp limit/rule triggered for user ${user.id}: ${e.message}" }
            twilioService.sendMessage(from, e.message ?: "Ação não permitida.")
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
            validateWhatsappQuota(user)
            
            val audioFile = twilioService.downloadMedia(mediaUrl)
            val transcribedText = aiTranscriptionService.transcribeAudioFromFile(audioFile)

            if (transcribedText.isBlank()) {
                logger.warn { "Transcription result for audio from $from is blank." }
                twilioService.sendMessage(from, "Desculpe, não consegui identificar o que você disse no áudio. Pode tentar novamente?")
                return
            }

            logger.info { "Audio from $from transcribed to: '$transcribedText'" }
            
            // A quota será consumida novamente dentro de processIncomingMessage, então não salvaremos aqui.
            processIncomingMessage(from, transcribedText)

        } catch (e: WhatsappSilentException) {
            logger.warn { "WhatsApp limit triggered for user ${user.id}, but warning was already sent recently. Silently dropping." }
        } catch (e: WhatsappIntegrationException) {
            logger.warn { "WhatsApp limit/rule triggered for user ${user.id}: ${e.message}" }
            twilioService.sendMessage(from, e.message ?: "Ação não permitida.")
        } catch (e: Exception) {
            logger.error(e) { "Error processing audio from $from" }
            twilioService.sendMessage(from, "Desculpe, ocorreu um erro ao processar seu áudio. Tente novamente mais tarde.")
        }
    }

    private fun sendWelcomeMessage(to: String) {
        val welcomeMsg = """
            Oi! 👋 Que bom ter você no FinSavior. Eu sou a Savi, sua assistente financeira inteligente! 💜

            Estou aqui para deixar o controle do seu dinheiro super simples. Você pode conversar comigo como se fosse uma amiga. Olha só alguns exemplos do que você pode me mandar:

            🍕 'Gastei 45 reais no iFood'
            💰 'Recebi 1500 de salário'
            📊 'Qual é o meu saldo deste mês?'

            Me conta: o que vamos registrar hoje?
        """.trimIndent()

        twilioService.sendMessage(to, welcomeMsg)
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
        return when (from.length) {
            13 -> {
                // Número sem o 9 (Ex: +55 84 9999999). Tenta buscar na base COM o 9.
                val ddd = from.substring(0, 5) // "+5584"
                val number = from.substring(5) // "8398888"
                userRepository.findByPhoneNumber("${ddd}9$number")
            }
            14 -> {
                // Número com o 9 (Ex: +55 84 99999999). Tenta buscar na base SEM o 9.
                val ddd = from.substring(0, 5) // "+5584"
                val number = from.substring(6) // Pula o 9
                userRepository.findByPhoneNumber("$ddd$number")
            }
            else -> {
                null
            }
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

        return user
    }

    private fun validateWhatsappQuota(user: User) {
        val planType = user.userPlan?.plan?.id?.let { CommonUtils.getPlanTypeById(it) } ?: PlanTypeEnum.FREE
        if (planType == PlanTypeEnum.FREE) {
            throw WhatsappIntegrationException("Esta é uma funcionalidade para usuários assinantes. Por favor, considere fazer um upgrade para um de nossos planos pagos.")
        }

        if (planType.maxWhatsappMessagesPerMonth != Int.MAX_VALUE) {
            val now = LocalDateTime.now()
            val startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
            val endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59)

            val usageCount = whatsappMessageHistoryRepository.countByUserIdAndCreatedAtBetween(user.id!!, startOfMonth, endOfMonth)

            if (usageCount >= planType.maxWhatsappMessagesPerMonth) {
                val lastWarning = lastWarningSentMap[user.id!!]
                if (lastWarning == null || lastWarning.isBefore(now.minusHours(1))) {
                    lastWarningSentMap[user.id!!] = now
                    throw WhatsappIntegrationException("Você atingiu o limite de ${planType.maxWhatsappMessagesPerMonth} mensagens via WhatsApp do seu plano. Use o aplicativo web ou faça upgrade para enviar mais.")
                } else {
                    throw WhatsappSilentException()
                }
            }
        }
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
