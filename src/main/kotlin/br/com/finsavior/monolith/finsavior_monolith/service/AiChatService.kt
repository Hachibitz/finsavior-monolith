package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.ChatbotException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ChatMessageDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.FinancialSummary
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.AiChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.AiChatResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ai.ollama.ChatMessage
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageEntity
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CurrentFinancialSituationEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.ChatMessageHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.ChatMessageRepository
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import mu.KLogger
import mu.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class AiChatService(
    private val userService: UserService,
    private val financialService: FinancialService,
    private val billService: BillService,
    private val chatMessageRepository: ChatMessageRepository,
    private val ollamaService: OllamaService,
    private val chatMessageHistoryRepository: ChatMessageHistoryRepository
) {

    private val log: KLogger = KotlinLogging.logger {}

    @Transactional
    fun chatWithAssistant(request: AiChatRequest): ResponseEntity<AiChatResponse> {
        val user = userService.getUserByContext()
        val userId = user.id

        validatePlanCoverage(user)

        val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val targetDate = LocalDateTime.now().format(formatter)
        val formattedDate = billService.formatBillDate(targetDate)

        val mainTableData = billService.loadMainTableData(formattedDate)
        val cardTableData = billService.loadCardTableData(formattedDate)
        val assetsTableData = billService.loadAssetsTableData(formattedDate)
        val paymentCardTableData = billService.loadPaymentCardTableData(formattedDate)

        val financialSummary = financialService.getUserFinancialSummary(userId!!, targetDate)

        val prompt = buildPrompt(
            financialSummary,
            request.question,
            targetDate,
            request.chatHistory,
            mainTableData,
            cardTableData,
            assetsTableData,
            paymentCardTableData
        )

        val messages = mutableListOf<ChatMessage>()
        messages += ChatMessage("user", prompt)

        val answer = try {
            ollamaService.chat(messages)
        } catch (e: Exception) {
            throw ChatbotException("Erro na comunicação com IA", e)
        }

        val dummyTokens = answer.length / 4
        val savedMessage = saveChatMessage(user, request, answer)
        saveChatMessageHistory(user.id!!, dummyTokens, savedMessage.id!!)

        return ResponseEntity.ok(AiChatResponse(answer))
    }

    fun chatWithAssistantStream(question: String): SseEmitter {
        val emitter = SseEmitter(Long.MAX_VALUE)
        val user = userService.getUserByContext()
        val userId = user.id!!

        validatePlanCoverage(user)

        val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val targetDate = LocalDateTime.now().format(formatter)
        val formattedDate = billService.formatBillDate(targetDate)

        val mainTableData = billService.loadMainTableData(formattedDate)
        val cardTableData = billService.loadCardTableData(formattedDate)
        val assetsTableData = billService.loadAssetsTableData(formattedDate)
        val paymentCardTableData = billService.loadPaymentCardTableData(formattedDate)

        val financialSummary = financialService.getUserFinancialSummary(userId, targetDate)

        val historyEntities = chatMessageRepository.findRecentMessages(userId)
        val chatHistory = historyEntities
            .asReversed()
            .flatMap { listOf(
                ChatMessage("user", it.userMessage),
                ChatMessage("assistant", it.assistantResponse)
            )}

        // Criar prompt com contexto e nova pergunta
        val prompt = buildPrompt(
            summary = financialSummary,
            question = question,
            date = targetDate,
            mainTableData = mainTableData,
            cardTableData = cardTableData,
            assetsTableData = assetsTableData,
            paymentCardTableData = paymentCardTableData
        )

        val messages = mutableListOf<ChatMessage>()
        chatHistory.forEach { messages.add(it) }
        messages += ChatMessage("user", prompt)

        val flow = ollamaService.chatStream(messages)
        val answerBuilder = StringBuilder()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                flow.collect {
                    log.info("SSE response: $it")
                    emitter.send(SseEmitter.event().data(it))
                    answerBuilder.append(it)
                }

                val finalAnswer = answerBuilder.toString()
                val savedMessage = saveChatMessage(user, AiChatRequest(question), finalAnswer)

                val dummyTokens = finalAnswer.length / 4
                saveChatMessageHistory(userId, dummyTokens, savedMessage.id!!)

                emitter.complete()

            } catch (e: Exception) {
                emitter.completeWithError(e)
            }
        }

        return emitter
    }

    fun getUserChatHistory(offset: Int, limit: Int): ResponseEntity<List<ChatMessageDTO>> {
        val user = userService.getUserByContext()
        val history = chatMessageRepository
            .findByUserIdOrderByCreatedAtDesc(user.id!!, PageRequest.of(offset / limit, limit))
            .map { ChatMessageDTO(it.userMessage, it.assistantResponse, it.createdAt) }
        return ResponseEntity.ok(history)
    }

    @Transactional
    fun clearUserChatHistory(): ResponseEntity<Void> {
        val user = userService.getUserByContext()
        chatMessageRepository.deleteByUserId(user.id!!)
        return ResponseEntity.noContent().build()
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

    private fun saveChatMessageHistory(userId: Long, dummyTokens: Int, chatMessageId: Long) {
        val history = ChatMessageHistory(
            userId = userId,
            chatMessageId = chatMessageId,
            tokensUsed = dummyTokens,
            createdAt = LocalDateTime.now()
        )
        chatMessageHistoryRepository.save(history)
    }

    private fun validatePlanCoverage(user: User) {
        if (!validateChatMessagesLimit(user)) {
            throw ChatbotException("Limite de mensagens atingido.")
        }
        if (!validateChatTokensLimit(user)) {
            throw ChatbotException("Limite de tokens atingido.")
        }
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
        summary: FinancialSummary,
        question: String,
        date: String? = null,
        chatHistory: List<String>? = null,
        mainTableData: List<BillTableDataDTO>,
        cardTableData: List<BillTableDataDTO>,
        assetsTableData: List<BillTableDataDTO>,
        paymentCardTableData: List<BillTableDataDTO>
    ): String {
        val period = date ?: "este mês"
        val situation = when (summary.currentSituation) {
            CurrentFinancialSituationEnum.VERMELHO -> "Negativa (vermelho)"
            CurrentFinancialSituationEnum.AMARELO -> "Atenção (amarelo)"
            CurrentFinancialSituationEnum.AZUL -> "Positiva (azul)"
        }

        val historySection = chatHistory?.takeLast(10)?.joinToString("\n") ?: "Sem histórico recente disponível."

        val mainData = formatTableSection("Despesas e receitas principais", mainTableData)
        val cardData = formatTableSection("Gastos no cartão de crédito", cardTableData)
        val assetData = formatTableSection("Caixa e ativos", assetsTableData)
        val cardPayments = formatTableSection("Pagamentos de fatura", paymentCardTableData)

        val foreseen = summary.foreseenBalance

        val accountGuide = """
            [GUIA DE CONTAS]
            • Saldo previsto: Saldo disponível após todas as contas serem pagas.
            • Saldo total: Saldo total disponível do mês (Caixa + Ativos).
            • Total de gastos: Somatório das contas do mês (Passivos e cartão).
            • Total não pago: Somatório do total de contas não pagas.
            • Total pago de cartão: Somatório dos pagamentos realizados no cartão de crédito.
            • Status atual: Diferença entre o saldo total e o total pago.
            • Liquidez: Diferença entre a soma dos ativos e o total de passivos.
        """.trimIndent()

        return """
        Você é uma assistente financeira pessoal chamada Savi que responde perguntas com base no resumo financeiro do usuário referente a $period.

        Use os dados abaixo para responder de forma objetiva, clara, precisa e amigável, sempre considerando o que foi perguntado.

        Se a pergunta do usuário solicitar valores ou recomendações específicas, você deve obrigatoriamente sugerir um valor estimado ou um intervalo numérico, mesmo que com ressalvas. Baseie sua resposta nos dados fornecidos e explique seu raciocínio de forma direta.

        Se for necessário ser cautelosa, ainda assim forneça um valor seguro com base no saldo disponível e nas despesas previstas.

        Caso a pergunta se refira a um mês diferente de $period, informe que você só pode responder com os dados atuais disponíveis.
        
        Importante levar em consideração o [HISTÓRICO DO CHAT] para não causar respostas repetitivas.
        
        Se a pergunta do usuário for sobre contas do mês atual (Ex.: "Em qual conta eu gastei mais?"), você deve responder depois de analisar os dados disponíveis em [DADOS DETALHADOS].

        $accountGuide

        [Resumo Financeiro de $period]
        • Situação atual: $situation
        • Saldo previsto: R$ $foreseen
        • Saldo total atual: R$ ${summary.totalBalance}
        • Total de gastos: R$ ${summary.totalExpenses}
        • Total de despesas não pagas: R$ ${summary.totalUnpaidExpenses}
        • Gastos no cartão de crédito: R$ ${summary.totalCreditCardExpense}
        • Total pago no cartão de crédito: R$ ${summary.totalPaidCreditCard}
        • Gastos por categoria:
        ${summary.categoryExpenses.entries.joinToString("\n") { "- ${it.key}: R$ ${it.value}" }}

        [RESUMO PARA INVESTIMENTO]
        Saldo para usar livremente. Ex.: investir, passear, comprar coisas, etc. (saldo previsto, após pagamento de todas as contas): R$ $foreseen

        [DADOS DETALHADOS]
        $mainData

        $cardData

        $assetData

        $cardPayments

        [PERGUNTA DO USUÁRIO]
        $question

        [RESPOSTA]
    """.trimIndent()
    }

    fun formatTableSection(title: String, rows: List<BillTableDataDTO>): String {
        if (rows.isEmpty()) return "$title: Nenhum dado encontrado."
        return buildString {
            appendLine("$title:")
            rows.forEach {
                appendLine("- ${it.billType}: ${it.billDescription} - R$ ${it.billValue} | Pago: ${if (it.paid) "Sim" else "Não"}")
            }
        }
    }

}