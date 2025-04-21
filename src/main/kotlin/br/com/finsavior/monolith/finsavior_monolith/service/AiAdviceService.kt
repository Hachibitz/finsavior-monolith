package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.PromptConfig
import br.com.finsavior.monolith.finsavior_monolith.exception.AiAdviceException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAdviceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAdviceResponseDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAnalysisDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.AiAdvice
import br.com.finsavior.monolith.finsavior_monolith.model.entity.AiAnalysisHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.AnalysisTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toAiAnalysisDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAdviceRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAnalysisHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.formatTableSection
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getAnalysisTypeById
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import org.springframework.ai.chat.ChatClient
import org.springframework.ai.chat.ChatResponse
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class AiAdviceService(
    private val chatClient: ChatClient,
    private val aiAdviceRepository: AiAdviceRepository,
    private val analysisHistoryRepository: AiAnalysisHistoryRepository,
    private val promptConfig: PromptConfig,
    @Lazy private val userService: UserService,
    private val billService: BillService,
    private val financialService: FinancialService,
) {

    fun getAiAdviceById(aiAdviceId: Long): AiAnalysisDTO =
        aiAdviceRepository.findById(aiAdviceId)
            .orElseThrow { AiAdviceException("Análise não encontrada") }.toAiAnalysisDTO()

    @Transactional
    fun generateAiAdviceWithAutoPrompt(request: AiAdviceDTO): AiAdviceResponseDTO {
        val user = userService.getUserByContext()
        val userId = user.id!!
        val now = LocalDateTime.now()

        val analysisType = getAnalysisTypeById(request.analysisTypeId)
            ?: throw AiAdviceException("Tipo de análise não encontrada")

        val planType = getPlanTypeById(user.userPlan!!.plan.id)
            ?: throw AiAdviceException("Plano não encontrado")

        val hasUsedFreeAnalysis = analysisHistoryRepository.existsByUserIdAndIsFreeAnalysisTrue(userId)

        if (!validatePlanAndAnalysisType(user, analysisType, planType, hasUsedFreeAnalysis)) {
            throw AiAdviceException("Consulta excedida pelo plano")
        }

        val monthStartDate = request.startDate
        val prompt = buildAnalysisPrompt(monthStartDate, analysisType, userId)

        val messages = mutableListOf<Message>(UserMessage(prompt))
        val options = OpenAiChatOptions.builder()
            .withModel("gpt-4o-mini")
            .withUser("FinSaviorApp")
            .withTemperature(if (planType == PlanTypeEnum.FREE) 0.0f else request.temperature)
            .withMaxTokens(
                when (analysisType) {
                    AnalysisTypeEnum.TRIMESTER -> 4500
                    AnalysisTypeEnum.ANNUAL -> 9000
                    else -> 2000
                }
            )
            .build()

        val chatResponse = try {
            chatClient.call(Prompt(messages, options))
        } catch (e: Exception) {
            throw AiAdviceException("Falha na comunicação com a API de IA", e)
        }

        val isFree = planType == PlanTypeEnum.FREE && !hasUsedFreeAnalysis
        val advice = saveAiAdvice(
            userId, request.copy(prompt = prompt), chatResponse, now, isFree
        )

        return AiAdviceResponseDTO(advice.id!!)
    }

    fun buildAnalysisPrompt(startingDate: LocalDateTime, analysisType: AnalysisTypeEnum, userId: Long): String {
        val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val targetDate = LocalDateTime.now().format(formatter)

        val monthSummaries = mutableListOf<String>()
        val mainTables = mutableListOf<String>()
        val cardTables = mutableListOf<String>()

        val months = (0 until analysisType.period).map {
            startingDate.plusMonths(it.toLong())
        }

        months.forEach { date ->
            val mainTable = billService.loadMainTableData(targetDate)
            val cardTable = billService.loadCardTableData(targetDate)
            val assetTable = billService.loadAssetsTableData(targetDate)
            val paymentCardTable = billService.loadPaymentCardTableData(targetDate)

            val summary = financialService.getUserFinancialSummary(userId, targetDate)

            monthSummaries += """
                ## ${formatter.format(date)}
                - Situação: ${summary.currentSituation}
                - Saldo Livre: R$ ${summary.foreseenBalance}
                - Liquidez: R$ ${summary.totalBalance - summary.totalExpenses}
                - Gastos Não Pagos: R$ ${summary.totalUnpaidExpenses}
            """.trimIndent()

            mainTables += formatTableSection("Tabelas principais", mainTable)
            mainTables += formatTableSection("Ativos", assetTable)
            cardTables += formatTableSection("Gastos no cartão", cardTable)
            cardTables += formatTableSection("Pagamentos de fatura", paymentCardTable)
        }

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
            # 🎯 Análise Financeira com IA — FinSavior
            Você é a **Savi**, assistente do app FinSavior. Faça uma análise profunda, estruturada e explicativa baseada nas informações abaixo:
        
            ## 🔢 Resumo Mensal
            ${monthSummaries.joinToString("\n\n")}
        
            ## 📋 Tabelas de Dados
            ${mainTables.joinToString("\n\n")}
        
            ## 💳 Cartões de Crédito
            ${cardTables.joinToString("\n\n")}
        
            ## ℹ️ Legendas
            $accountGuide
        
            ## 🧠 Objetivo
            Faça uma análise clara e direta das minhas finanças.
            - Estou indo bem?
            - Há algo preocupante?
            - Em que posso melhorar?
            - Dicas para economizar ou me organizar melhor?
        
            ## 📌 Instruções
            - Use **markdown** (títulos, listas, negrito, emojis)
            - Destaque números importantes
            - Seja útil, empático e técnico
            - Use linguagem acessível e organizada
        
            ---
            Resposta:
        """.trimIndent()
    }

    fun getAiAnalysisList(): List<AiAnalysisDTO> {
        try {
            val userId: Long = userService.getUserByContext().id!!
            val responseAiAnalysisList: MutableList<AiAnalysisDTO> = mutableListOf()
            val aiAdviceList: List<AiAdvice?>? = aiAdviceRepository.getAllByUserId(userId)
            aiAdviceList?.map { aiAdvice -> aiAdvice?.let { responseAiAnalysisList.add(it.toAiAnalysisDTO()) } }
            return responseAiAnalysisList
        } catch (e: Exception) {
            throw AiAdviceException("Falha ao carregar análises: ${e.message}")
        }
    }

    fun deleteAnalysis(analysisId: Long) {
        try {
            aiAdviceRepository.deleteById(analysisId)
        } catch (e: Exception) {
            throw AiAdviceException("Falha ao deletar análise: ${e.message}")
        }
    }

    fun validateHasCoverage(analysisTypeId: Int): Boolean {
        val user: User = userService.getUserByContext()

        val analysisType = getAnalysisTypeById(analysisTypeId) ?:
        throw AiAdviceException("Tipo de análise não encontrada")
        val planType: PlanTypeEnum = getPlanTypeById(user.userPlan!!.plan.id) ?:
        throw AiAdviceException("Plano não encontrado")

        val hasUsedFreeAnalysis = analysisHistoryRepository.existsByUserIdAndIsFreeAnalysisTrue(user.id!!)

        return validatePlanAndAnalysisType(
            user,
            analysisType,
            planType,
            hasUsedFreeAnalysis
        )
    }

    private fun validatePlanAndAnalysisType(
        user: User,
        analysisType: AnalysisTypeEnum,
        planType: PlanTypeEnum,
        hasUsedFreeAnalysis: Boolean,
    ): Boolean {
        if (planType == PlanTypeEnum.FREE && hasUsedFreeAnalysis) {
            return false
        }

        val userId: Long = user.id!!
        val currentDate = LocalDateTime.now()
        val yearMonth = YearMonth.of(currentDate.year, currentDate.month)
        val daysOfMonth = yearMonth.lengthOfMonth()

        val initialDate = currentDate.withDayOfMonth(1)
        val endDate = currentDate.withDayOfMonth(daysOfMonth)

        val annualAiAdvicesOfMonth: Int = analysisHistoryRepository.countByUserIdAndAnalysisTypeIdAndDateBetween(
            userId,
            AnalysisTypeEnum.ANNUAL.analysisTypeId,
            initialDate,
            endDate
        )
        val trimesterAiAdvicesOfMonth: Int = analysisHistoryRepository.countByUserIdAndAnalysisTypeIdAndDateBetween(
            userId,
            AnalysisTypeEnum.TRIMESTER.analysisTypeId,
            initialDate,
            endDate
        )
        val monthAiAdvicesOfMonth: Int = analysisHistoryRepository.countByUserIdAndAnalysisTypeIdAndDateBetween(
            userId,
            AnalysisTypeEnum.MONTH.analysisTypeId,
            initialDate,
            endDate
        )

        when (analysisType) {
            AnalysisTypeEnum.ANNUAL -> {
                if (annualAiAdvicesOfMonth >= planType.amountOfAnnualAnalysisPerMonth) {
                    return false
                }
            }

            AnalysisTypeEnum.TRIMESTER -> {
                if (trimesterAiAdvicesOfMonth >= planType.amountOfTrimesterAnalysisPerMonth) {
                    return false
                }
            }

            AnalysisTypeEnum.MONTH -> if (monthAiAdvicesOfMonth >= planType.amountOfMonthAnalysisPerMonth) {
                return false
            }
        }

        return true
    }

    private fun saveAiAdvice(
        userId: Long,
        request: AiAdviceDTO,
        chatResponse: ChatResponse,
        generatedAt: LocalDateTime,
        isFree: Boolean
    ): AiAdvice {
        val aiAdvice = aiAdviceRepository.save(
            AiAdvice(
                userId = userId,
                resultMessage = chatResponse.result.output.content,
                analysisTypeId = request.analysisTypeId,
                temperature = request.temperature,
                date = generatedAt,
                startDate = request.startDate,
                finishDate = request.finishDate,
                audit = Audit()
            )
        )

        analysisHistoryRepository.save(
            AiAnalysisHistory(
                userId = userId,
                analysisTypeId = request.analysisTypeId,
                date = generatedAt,
                isFreeAnalysis = isFree,
                audit = Audit()
            )
        )

        return aiAdvice
    }
}