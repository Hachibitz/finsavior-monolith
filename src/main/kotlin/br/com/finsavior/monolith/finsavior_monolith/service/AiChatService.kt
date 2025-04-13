package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.ChatbotException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ChatMessageDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.FinancialSummary
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessage
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CurrentFinancialSituationEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.ChatMessageHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.ChatMessageRepository
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import org.springframework.ai.chat.ChatClient
import org.springframework.ai.chat.ChatResponse
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class AiChatService(
    private val userService: UserService,
    private val financialService: FinancialService,
    private val chatClient: ChatClient,
    private val billService: BillService,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatMessageHistoryRepository: ChatMessageHistoryRepository
) {

    fun askQuestion(prompt: String): ChatResponse {
        val messages = listOf(
            SystemMessage("Você é um assistente financeiro inteligente."),
            UserMessage(prompt)
        )

        val options = OpenAiChatOptions.builder()
            .withModel("gpt-3.5-turbo-1106")
            .withUser("FinSaviorApp")
            .withTemperature(0.2f)
            .withMaxTokens(1000)
            .build()

        return try {
            val response = chatClient.call(Prompt(messages, options))
            response
        } catch (e: Exception) {
            throw ChatbotException("Erro ao se comunicar com o assistente: ${e.message}", e)
        }
    }


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
        val chatResponse = askQuestion(prompt)
        val answer = chatResponse.result.output.content
        val totalTokensFromOpenAI = chatResponse.metadata.usage.totalTokens
        val savedMessage = saveChatMessage(user, request, answer, totalTokensFromOpenAI)
        saveChatMessageHistory(user.id!!, totalTokensFromOpenAI, savedMessage.id!!)

        return ResponseEntity.ok(AiChatResponse(answer))
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

    private fun saveChatMessage(user: User, request: AiChatRequest, aiAnswer: String, totalTokensFromOpenAI: Long): ChatMessage {
        val message = ChatMessage(
            userId = user.id!!,
            userMessage = request.question,
            assistantResponse = aiAnswer,
            createdAt = LocalDateTime.now()
        )
        return chatMessageRepository.save(message)
    }

    private fun saveChatMessageHistory(userId: Long, totalTokensFromOpenAI: Long, chatMessageId: Long) {
        val history = ChatMessageHistory(
            userId = userId,
            chatMessageId = chatMessageId,
            tokensUsed = totalTokensFromOpenAI,
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

        Use os dados abaixo para responder de forma **objetiva, clara, precisa e amigável**, sempre considerando o que foi perguntado.

        Se a pergunta do usuário solicitar **valores ou recomendações específicas**, você **deve obrigatoriamente sugerir um valor estimado** ou um intervalo numérico, mesmo que com ressalvas. Baseie sua resposta nos dados fornecidos e explique seu raciocínio de forma direta.

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

        [HISTÓRICO DO CHAT]
        $historySection

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