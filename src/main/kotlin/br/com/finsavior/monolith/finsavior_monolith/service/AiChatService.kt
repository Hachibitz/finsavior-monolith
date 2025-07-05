package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.ai.MCPToolsConfig
import br.com.finsavior.monolith.finsavior_monolith.exception.ChatbotException
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
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getDateGuidelines
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getFallbackRules
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getFormatOfResponse
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getMcpToolsDescription
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getResponseGuidelines
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getResponseStructure
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getSaviDescription
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
    private val mcpToolsConfig: MCPToolsConfig
) {

    private val log: KLogger = KotlinLogging.logger {}

    fun askQuestion(prompt: String): Response<AiMessage> {
        log.info("Asking question to Savi Assistant")
        val userId = userService.getUserByContext().id!!

        val aiServices = AiServices
            .builder(SaviAssistant::class.java)
            .maxSequentialToolsInvocations(5)
            .chatLanguageModel(chatModel)
            .tools(mcpToolsConfig)
            .build()

        val messages = getMessagesWithChatHistoryAndSystemMessage(userId, prompt)

        return try {
            aiServices.chat(messages)
        } catch (e: Exception) {
            throw ChatbotException("Erro ao se comunicar com o assistente: ${e.message}", e)
        }
    }

    @Transactional
    fun chatWithAssistant(request: AiChatRequest): ResponseEntity<AiChatResponse> {
        log.info("Incoming chat request: ${request.question}")
        val user = userService.getUserByContext()
        val userId = user.id!!

        validatePlanCoverage(user)

        val prompt = buildPrompt(userId, request.question)
        val chatResponse = askQuestion(prompt)
        val answer = chatResponse.content().text()

        val totalTokensFromOpenAI = chatResponse.tokenUsage().totalTokenCount()
        val savedMessage = saveChatMessage(user, request, answer!!)
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

    fun getUserChatHistoryDTO(userId: Long, offset: Int, limit: Int) =
        chatMessageRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(offset / limit, limit))
            .map { ChatMessageDTO(it.userMessage, it.assistantResponse, it.createdAt) }

    private fun getMessagesWithChatHistoryAndSystemMessage(userId: Long, prompt: String): List<ChatMessage> {
        val systemMessage = SystemMessage.from("""
            Você é a Savi, assistente financeira. 
            Use as ferramentas MCP sempre que precisar de dados.
            NÃO explique que vai usar as ferramentas, apenas use-as silenciosamente.
            Formate datas como 'Mmm yyyy' (ex: 'Oct 2025').
        """.trimIndent())

        val historyMessages = getUserChatHistoryDTO(userId, 0, 20).flatMap { e ->
            listOf(
                UserMessage(e.userMessage),
                AiMessage.builder().text(e.assistantResponse).build()
            )
        }

        return listOf(systemMessage) + historyMessages + UserMessage.from(prompt)
    }

    private fun saveChatMessage(user: User, request: AiChatRequest, aiAnswer: String): ChatMessageEntity {
        val message = ChatMessageEntity(
            userId = user.id!!,
            userMessage = request.question,
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

    private fun buildPrompt(
        userId: Long,
        question: String
    ): String {
        log.info("Building prompt for user: $userId with question: $question")
        val currentDate = LocalDateTime.now()
        val accountGuide = getAccountGuide()
        val saviDescription = getSaviDescription()
        val mcpToolsDescription = getMcpToolsDescription()
        val fallbackRules = getFallbackRules(userId)
        val dataSearchingStrategy = getSearchingDataStrategy(userId)
        val responseGuidelines = getResponseGuidelines()
        val dateGuidelines = getDateGuidelines(currentDate)
        val responseStructure = getResponseStructure()
        val formatOfResponse = getFormatOfResponse()

        return """
            $saviDescription
            
            $mcpToolsDescription
            
            $fallbackRules
            
            $dataSearchingStrategy
        
            $responseGuidelines
            
            $dateGuidelines
            
            $responseStructure
            
            # Id do usuário para uso no MCP Tools
            "$userId"
            
            # Guia de contas
            "$accountGuide"
            
            # Pergunta Atual do usuário
            "$question"
            
            # Formato da Resposta
            $formatOfResponse
        """.trimIndent()
    }
}