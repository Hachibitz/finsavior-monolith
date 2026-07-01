package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.ai.MCPToolsConfig
import br.com.finsavior.monolith.finsavior_monolith.config.ai.OpenAiModelConfig
import br.com.finsavior.monolith.finsavior_monolith.exception.ChatbotException
import br.com.finsavior.monolith.finsavior_monolith.util.AiExceptionSupport
import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ChatMessageDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageEntity
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.repository.ChatMessageHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.ChatMessageRepository
import br.com.finsavior.monolith.finsavior_monolith.service.strategy.SaviAssistant
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getAccountGuide
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getChatResponseFormat
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getChatResponseGuidelines
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getChatSaviDescription
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getDateGuidelines
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getFallbackRules
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getSearchingDataStrategy
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.output.Response
import dev.langchain4j.service.AiServices
import mu.KLogger
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime


@Service
class AiChatService(
    private val userService: UserService,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatMessageHistoryRepository: ChatMessageHistoryRepository,
    private val chatModel: ChatLanguageModel,
    private val mcpToolsConfig: MCPToolsConfig,
    private val fsCoinService: FsCoinService,
    @param:Value("\${fscoins-cost-for-chat}") private val coinsCostForMessage: Long
) {

    private val log: KLogger = KotlinLogging.logger {}

    fun askQuestion(userId: Long, question: String): Response<AiMessage> {
        log.info("Asking question to Savi Assistant for user {}", userId)

        val aiServices = AiServices
            .builder(SaviAssistant::class.java)
            .maxSequentialToolsInvocations(OpenAiModelConfig.CHAT_MAX_SEQUENTIAL_TOOL_INVOCATIONS)
            .chatLanguageModel(chatModel)
            .tools(mcpToolsConfig)
            .build()

        val messages = getMessagesWithChatHistoryAndSystemMessage(userId, question)

        return try {
            aiServices.chat(messages)
        } catch (e: Exception) {
            throw AiExceptionSupport.chatCommunicationFailure(e)
        }
    }

    @Transactional
    fun chatWithAssistant(request: AiChatRequest): ResponseEntity<AiChatResponse> {
        log.info("Incoming chat request: ${request.question}")
        val user = userService.getUserByContext()
        val userId = user.id!!

        if (request.isUsingCoins != null && request.isUsingCoins) {
            val fsCoinBalance = fsCoinService.getBalance(user.id)
            validateUsingCoins(user.id!!, fsCoinBalance)
            useCoinsForChat(user.id!!, fsCoinBalance)
        } else {
            validatePlanCoverage(user)
        }

        val question = truncateText(request.question.trim(), OpenAiModelConfig.CHAT_QUESTION_MAX_CHARS)
        if (question.isBlank()) {
            throw ChatbotException("Pergunta inválida.")
        }

        val chatResponse = askQuestion(userId, question)
        val answer = chatResponse.content().text()

        val totalTokensFromOpenAI = chatResponse.tokenUsage().totalTokenCount()
        val savedMessage = saveChatMessage(user, question, answer!!)
        saveChatMessageHistory(userId, totalTokensFromOpenAI, savedMessage.id!!)

        return ResponseEntity.ok(AiChatResponse(answer))
    }

    fun getUserChatHistory(offset: Int, limit: Int): ResponseEntity<List<ChatMessageDTO>> {
        val userId = userService.getUserByContext().id!!
        val history = getUserChatHistoryDTO(userId, offset, limit)
        return ResponseEntity.ok(history)
    }

    @Transactional
    fun clearUserChatHistory(): ResponseEntity<Void> {
        val user = userService.getUserByContext()
        chatMessageRepository.deleteByUserId(user.id!!)
        return ResponseEntity.noContent().build()
    }

    fun getUserChatHistoryDTO(userId: Long, offset: Int, limit: Int): List<ChatMessageDTO> =
        chatMessageRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(offset / limit, limit))
            .asReversed()
            .map { ChatMessageDTO(it.userMessage, it.assistantResponse, it.createdAt) }

    private fun getMessagesWithChatHistoryAndSystemMessage(userId: Long, question: String): List<ChatMessage> {
        val systemMessage = buildChatSystemMessage(userId)

        val historyMessages = getUserChatHistoryDTO(userId, 0, OpenAiModelConfig.CHAT_HISTORY_EXCHANGES)
            .flatMap { entry ->
                listOf(
                    UserMessage.from(truncateText(entry.userMessage, OpenAiModelConfig.CHAT_MESSAGE_MAX_CHARS)),
                    AiMessage.builder()
                        .text(truncateText(entry.assistantResponse, OpenAiModelConfig.CHAT_MESSAGE_MAX_CHARS))
                        .build()
                )
            }

        return listOf(systemMessage) + historyMessages + UserMessage.from(question)
    }

    private fun buildChatSystemMessage(userId: Long): SystemMessage {
        val currentDate = LocalDateTime.now()
        val billTableGuide = """
            # Guia de Tipo de Conta (billTable)
            - `CREDIT_CARD`: crédito, cartão, fatura ou parcelado.
            - `ASSETS`: salário, recebimentos, bônus.
            - `PAYMENT_CARD`: pagamento de fatura de cartão.
            - `MAIN`: demais despesas.
        """.trimIndent()

        return SystemMessage.from(
            """
            Você é a Savi, assistente financeira do FinSavior.
            Use as ferramentas MCP quando precisar de dados (incluindo loadUserGoals para metas financeiras).
            NÃO explique que vai usar ferramentas, apenas use-as silenciosamente.
            Formate datas como 'Mmm yyyy' (ex: 'Oct 2025').

            ${getChatSaviDescription()}

            ${getFallbackRules(userId)}

            ${getSearchingDataStrategy(userId)}

            ${getChatResponseGuidelines()}

            ${getDateGuidelines(currentDate)}

            ${getChatResponseFormat()}

            # Id do usuário para uso no MCP Tools
            $userId

            # Guia de contas
            ${getAccountGuide()}

            $billTableGuide
            """.trimIndent()
        )
    }

    private fun truncateText(value: String, maxChars: Int): String =
        if (value.length <= maxChars) value else value.take(maxChars - 1) + "…"

    private fun saveChatMessage(
        user: User,
        question: String,
        aiAnswer: String
    ): ChatMessageEntity {
        val message = ChatMessageEntity(
            userId = user.id!!,
            userMessage = question,
            assistantResponse = aiAnswer,
            createdAt = LocalDateTime.now()
        )
        return chatMessageRepository.save(message)
    }

    private fun saveChatMessageHistory(userId: Long, totalTokensFromOpenAI: Int, chatMessageId: Long) {
        val history = ChatMessageHistory(
            userId = userId,
            chatMessageId = chatMessageId,
            tokensUsed = totalTokensFromOpenAI.toLong(),
            createdAt = LocalDateTime.now()
        )
        chatMessageHistoryRepository.save(history)
    }

    private fun validateUsingCoins(userId: Long, fsCoinBalance: Long) {
        log.info("Validating coins usage for user: $userId")
        if (fsCoinBalance <= 9) {
            log.error("Insufficient coins for user: $userId")
            throw InsufficientFsCoinsException("Saldo insuficiente de moedas.")
        }
    }

    private fun useCoinsForChat(userId: Long, fsCoinBalance: Long) {
        fsCoinService.spendCoins(coinsCostForMessage, userId)
        log.info("Deducted $coinsCostForMessage coins for user: $userId, new balance: $fsCoinBalance-$coinsCostForMessage")
    }

    private fun validatePlanCoverage(user: User) {
        log.info("Validating plan coverage for user: ${user.id}")
        if (!validateChatMessagesLimit(user)) {
            log.error("Chat messages limit exceeded for user: ${user.id}")
            throw ChatbotException("Limite de mensagens atingido.")
        }
        if (!validateChatTokensLimit(user)) {
            log.error("Chat tokens limit exceeded for user: ${user.id}")
            throw ChatbotException("Limite de tokens atingido.")
        }
        log.info("User ${user.id} has valid plan coverage for chat request")
    }

    private fun validateChatMessagesLimit(
        user: User
    ): Boolean {
        val now = LocalDateTime.now()
        val startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
        val endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59)

        val messagesThisMonth = chatMessageHistoryRepository.countByUserIdAndCreatedAtBetween(user.id!!, startOfMonth, endOfMonth)

        val planId = user.userPlan!!.plan.id
        val planType = getPlanTypeById(planId)

        return messagesThisMonth < planType!!.amountOfChatMessagesWithSavi
    }

    fun validateChatTokensLimit(user: User): Boolean {
        val now = LocalDateTime.now()
        val startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
        val endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59)

        val messages = chatMessageHistoryRepository.findAllByUserIdAndCreatedAtBetween(user.id!!, startOfMonth, endOfMonth)

        val planId = user.userPlan!!.plan.id
        val planType = getPlanTypeById(planId)

        val totalTokensUsed = messages.sumOf { it.tokensUsed }

        return totalTokensUsed < planType!!.maxTokensPerMonth
    }
}
