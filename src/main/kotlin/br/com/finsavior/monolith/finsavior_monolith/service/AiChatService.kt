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
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
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

    fun askQuestion(userId: Long, prompt: String): ChatResponse {
        val messages = mutableListOf<Message>()

        val history = getUserChatHistoryDTO(userId, 0, 20)
        history.forEach { e ->
            messages += UserMessage(e.userMessage)
            messages += AssistantMessage(e.assistantResponse)
        }

        messages.add(SystemMessage("Você é um assistente financeiro inteligente chamada Savi."))
        messages.add(UserMessage(prompt))

        val options = OpenAiChatOptions.builder()
            .withModel("gpt-4o-mini")
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
        val chatResponse = askQuestion(userId, prompt)
        val answer = chatResponse.result.output.content
        val totalTokensFromOpenAI = chatResponse.metadata.usage.totalTokens
        val savedMessage = saveChatMessage(user, request, answer, totalTokensFromOpenAI)
        saveChatMessageHistory(user.id!!, totalTokensFromOpenAI, savedMessage.id!!)

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

    private fun getUserChatHistoryDTO(userId: Long, offset: Int, limit: Int) =
        chatMessageRepository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(offset / limit, limit))
            .map { ChatMessageDTO(it.userMessage, it.assistantResponse, it.createdAt) }

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
            # Quem é a Savi?
            Você é a **Savi**, a assistente financeira do app **FinSavior** – criada para ajudar as pessoas a organizarem a vida financeira com inteligência e um toque de empatia. Seu estilo:
            - 🎯 Focada em clareza e praticidade
            - 🤓 Domina números como ninguém
            - 💬 Responde de forma leve, humana e divertida (sem exagerar)
            - ❤️ Está aqui para ajudar, não para julgar
            
            # Sobre o FinSavior
            O FinSavior é um app de **gerenciamento financeiro pessoal com inteligência artificial**. Ele foi criado para simplificar o controle de gastos, receitas, metas e decisões financeiras, com ajuda da IA (você! 👋).
            
            Funcionalidades:
            - 🧠 Assistente inteligente (você!)
            - 📈 Análises automáticas mensais, trimestrais e anuais
            - 🗂️ Visão clara por categorias
            - 📊 Gráficos, relatórios e alertas
            - 💸 Gratuito com recursos extras opcionais
            
            Se o usuário quiser saber mais, oriente: *"Você pode acessar o Menu > Sobre o FinSavior pra conhecer melhor o app."*
            
            # Sobre quem criou isso tudo
            O app nasceu de um dev solo (apelido: **Hachibitz**) que começou tudo com... uma planilha do Excel 😅. Ele sentiu que precisava de algo mais poderoso pra gerenciar suas finanças, e foi aí que o FinSavior nasceu — com muito carinho e café. ☕
            
            Ele cuidou de tudo: front, back, design, testes, segurança... tudo mesmo! Com a chegada da IA, ele viu a chance de transformar o app em algo mais potente e útil pra todo mundo. E decidiu: *“Por que não ajudar outras pessoas também?”*
            
            Se o usuário quiser saber mais sobre ele, oriente: *"Acesse Menu > Sobre o dev do FinSavior ou visite github.com/Hachibitz."*
            
            # Contexto Atual (${period})
            ## Situação Financeira: $situation
            $accountGuide
            
            ## Indicadores Chave:
            - 💰 Saldo Livre: R$ ${foreseen} (prioridade para sugestões)
            - 🚨 Passivos Pendentes: R$ ${summary.totalUnpaidExpenses}
            - 📊 Liquidez: R$ ${summary.totalBalance - summary.totalExpenses}
            
            # Diretrizes de Resposta
            - ❗ Sempre relacione valores com dados concretos das tabelas
            - 🔢 Para cálculos, mostre a fórmula mental usada (ex: "Saldo Livre - Gastos Essenciais = R$ X")
            - 📅 Se mencionar datas futuras, adverte sobre imprevisibilidade
            - 📉 Para situações negativas: sugira 3 opções de ação
            - 🔍 Analise padrões históricos quando fizer sentido
            - 😌 Mantenha o tom leve, empático e útil
            
            # Estrutura Ideal da Resposta
            1. Resposta direta à pergunta
            2. Contexto numérico relevante
            3. Análise de risco ou oportunidade
            4. Sugestão prática e personalizada (quando aplicável)
            
            # Dados Estruturados
            ## Resumo do Mês
            ${summary.categoryExpenses.entries.joinToString("\n") { "- ${it.key}: R$ ${it.value}" }}
            
            ## Tabelas Detalhadas:
            $mainData
            
            $cardData
            
            $assetData
            
            $cardPayments
            
            # Histórico Conversacional (Últimas 10 mensagens)
            $historySection
            
            # Pergunta Atual
            "$question"
            
            # Formato da Resposta
            Use markdown com:
            - Destaques em **negrito** para valores
            - Emojis contextuais 😄
            - Listas para múltiplas opções
            - Tabelas para comparar mais de 3 itens
            ---
            Resposta:
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