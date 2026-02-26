package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.ai.MCPToolsConfig
import br.com.finsavior.monolith.finsavior_monolith.exception.AiAdviceException
import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAdviceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAdviceResponseDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiAnalysisDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.QuickInsightDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.AiAdvice
import br.com.finsavior.monolith.finsavior_monolith.model.entity.AiAnalysisHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.AnalysisTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toAiAnalysisDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAdviceRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAnalysisHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.service.strategy.SaviAssistant
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getAccountGuide
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getAnalysisTypeGuide
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getFallbackRules
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getMcpToolsDescription
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getResponseGuidelines
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getResponseStructure
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getSaviDescription
import br.com.finsavior.monolith.finsavior_monolith.util.AiUtils.Companion.getSearchingDataStrategy
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getAnalysisTypeById
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI
import dev.langchain4j.model.output.Response
import dev.langchain4j.service.AiServices
import mu.KLogger
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class AiAdviceService(
    private val aiAdviceRepository: AiAdviceRepository,
    private val analysisHistoryRepository: AiAnalysisHistoryRepository,
    @Lazy private val userService: UserService,
    @Value("\${ai.openai.api-key}") private val openAiApiKey: String,
    private val fsCoinService: FsCoinService,
    private val financialService: FinancialService,
    private val cacheManager: CacheManager,
) {

    @Autowired
    lateinit var mcpToolsConfig: MCPToolsConfig

    private val log: KLogger = KotlinLogging.logger {}

    private data class QuickInsightCacheEntry(val insight: QuickInsightDTO, val baseline: BigDecimal)

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

        if (request.isUsingCoins) {
            val coinsCost = fsCoinService.getCoinsCostForAnalysis(analysisType)
            if (!validateCoinsUsage(user, analysisType, coinsCost)) {
                throw InsufficientFsCoinsException("Usuário não possui FsCoins suficientes para realizar a análise: ${analysisType.name}")
            }
            fsCoinService.spendCoins(coinsCost, userId)
        } else {
            if (!validatePlanAndAnalysisType(user, analysisType, planType, hasUsedFreeAnalysis)) {
                throw AiAdviceException("Consulta excedida pelo plano")
            }
        }

        val monthStartDate = request.startDate
        val prompt = buildAnalysisPrompt(monthStartDate, analysisType, userId)

        val chatModel = OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(GPT_4_O_MINI)
            .temperature((if (planType == PlanTypeEnum.FREE) 0.0f else request.temperature).toDouble())
            .maxTokens(
                when (analysisType) {
                    AnalysisTypeEnum.TRIMESTER -> 4500
                    AnalysisTypeEnum.ANNUAL -> 9000
                    else -> 2000
                }
            )
            .build()

        val aiService = AiServices
            .builder(SaviAssistant::class.java)
            .chatLanguageModel(chatModel)
            .tools(mcpToolsConfig)
            .build()

        val chatResponse = try {
            val systemMessage = SystemMessage.from("""
                Você é a Savi, assistente financeira. 
                Use as ferramentas MCP sempre que precisar de dados.
                NÃO explique que vai usar as ferramentas, apenas use-as silenciosamente.
                Formate datas como 'Mmm yyyy' (ex: 'Oct 2025').
            """.trimIndent())
            val messages = listOf(
                systemMessage,
                UserMessage.from(prompt)
            )
            aiService.chat(messages)
        } catch (e: Exception) {
            throw AiAdviceException("Falha na comunicação com a API de IA", e)
        }

        val isFree = planType == PlanTypeEnum.FREE && !hasUsedFreeAnalysis
        val advice = saveAiAdvice(
            userId, request.copy(prompt = prompt), chatResponse, now, isFree, request.isUsingCoins
        )

        return AiAdviceResponseDTO(advice.id!!)
    }

    fun buildAnalysisPrompt(startingDate: LocalDateTime, analysisType: AnalysisTypeEnum, userId: Long): String {
        val accountGuide = getAccountGuide()
        val saviDescription = getSaviDescription()
        val mcpToolsDescription = getMcpToolsDescription()
        val fallbackRules = getFallbackRules(userId)
        val dataSearchingStrategy = getSearchingDataStrategy(userId)
        val responseGuidelines = getResponseGuidelines()
        val responseStructure = getResponseStructure()
        val analysisTypeGuide = getAnalysisTypeGuide()

        return """
            # 🎯 Análise Financeira com IA — FinSavior
            
            $saviDescription 
            # Por favor, faça uma análise minuciosa do tipo **$analysisType** de minhas finanças a partir de **$startingDate**:
            
            $mcpToolsDescription
            
            $fallbackRules
            
            $dataSearchingStrategy
            
            $responseGuidelines
            
            $responseStructure
        
            # Guia de tipo de análises:
            "$analysisTypeGuide"
            
            # Guia de contas
            "$accountGuide"
            
            # Id do usuário para uso no MCP Tools
            "$userId"
        
            ## 🧠 Objetivo da análise
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

        val annualAiAdvicesOfMonth: Int = analysisHistoryRepository.countByUserIdAndAnalysisTypeIdAndDateBetweenAndIsUsingFsCoinsFalse(
            userId,
            AnalysisTypeEnum.ANNUAL.analysisTypeId,
            initialDate,
            endDate
        )
        val trimesterAiAdvicesOfMonth: Int = analysisHistoryRepository.countByUserIdAndAnalysisTypeIdAndDateBetweenAndIsUsingFsCoinsFalse(
            userId,
            AnalysisTypeEnum.TRIMESTER.analysisTypeId,
            initialDate,
            endDate
        )
        val monthAiAdvicesOfMonth: Int = analysisHistoryRepository.countByUserIdAndAnalysisTypeIdAndDateBetweenAndIsUsingFsCoinsFalse(
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

    private fun validateCoinsUsage(
        user: User,
        analysisType: AnalysisTypeEnum,
        coinsCostForAnalysis: Long
    ) =
        fsCoinService.hasEnoughCoinsForAnalysis(analysisType, user.id, coinsCostForAnalysis)

    private fun saveAiAdvice(
        userId: Long,
        request: AiAdviceDTO,
        chatResponse: Response<AiMessage>,
        generatedAt: LocalDateTime,
        isFree: Boolean,
        isUsingFsCoins: Boolean,
    ): AiAdvice {
        val aiAdvice = aiAdviceRepository.save(
            AiAdvice(
                userId = userId,
                resultMessage = chatResponse.content().text(),
                analysisTypeId = request.analysisTypeId,
                temperature = request.temperature,
                date = generatedAt,
                startDate = request.startDate,
                finishDate = request.finishDate,
                audit = Audit(),
            )
        )

        analysisHistoryRepository.save(
            AiAnalysisHistory(
                userId = userId,
                analysisTypeId = request.analysisTypeId,
                date = generatedAt,
                isFreeAnalysis = isFree,
                audit = Audit(),
                isUsingFsCoins = isUsingFsCoins
            )
        )

        return aiAdvice
    }

    fun getQuickInsight(date: String?): QuickInsightDTO {
        val userId = getContextUserId()

        try {
            val summary = financialService.getUserFinancialSummary(userId, date)
            val currentBaseline = summary.foreseenBalance

            val cacheKey = "${'$'}{userId}_$date"

            val cache: Cache? = cacheManager.getCache("quickInsight")

            val cachedEntry = cache?.get(cacheKey, QuickInsightCacheEntry::class.java)
            if (cachedEntry != null) {
                if (!hasChangedMoreThanPercent(cachedEntry.baseline, currentBaseline, BigDecimal(10))) {
                    log.info("quickInsight cache hit for userId=$userId date=$date")
                    return cachedEntry.insight
                } else {
                    log.info("quickInsight cache invalidated for userId=$userId date=$date due to baseline change")
                }
            }

            val systemPrompt = getQuickInsightSystemPrompt()
            val accountGuide = getAccountGuide()
            val userPrompt = buildQuickInsightPrompt(summary, accountGuide)

            val aiResponse = callAiForQuickInsight(systemPrompt, userPrompt)
            val result = if (isValidAiInsight(aiResponse)) QuickInsightDTO(aiResponse!!.trim())
            else QuickInsightDTO(truncateInsight(computeFallbackInsight(summary)))

            refreshQuickInsightCacheForUser(cache, cacheKey, result, currentBaseline, userId, date)

            return result

        } catch (e: Exception) {
            throw AiAdviceException("Falha ao gerar quick insight: ${e.message}")
        }
    }

    fun getContextUserId(): Long = userService.getUserByContext().id!!

    private fun hasChangedMoreThanPercent(previous: BigDecimal, current: BigDecimal, percentThreshold: BigDecimal): Boolean {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) != 0
        }
        val diff = current.subtract(previous).abs()
        val changePercent = diff.divide(previous, 4, RoundingMode.HALF_UP).multiply(BigDecimal(100))
        return changePercent > percentThreshold
    }

    private fun refreshQuickInsightCacheForUser(
        cache: Cache?,
        cacheKey: String,
        result: QuickInsightDTO,
        currentBaseline: BigDecimal,
        userId: Long,
        date: String?
    ) {
        try {
            cache?.put(cacheKey, QuickInsightCacheEntry(result, currentBaseline))
        } catch (e: Exception) {
            log.warn("Failed to cache quick insight for user=$userId date=$date, but insight generation succeeded. Error ignorado para não impactar usuário. Detalhes: ${e.message}")
        }
    }

    private fun getQuickInsightSystemPrompt(): SystemMessage = SystemMessage.from(
        """
            Você é o Savi, um assistente financeiro inteligente, motivador e direto. Sua tarefa é analisar os dados financeiros do usuário (receitas, despesas e categorias) para o mês solicitado e gerar um único insight curto e impactante.
            Regras:
            - O insight deve ter no máximo 150 caracteres.
            - Use um tom encorajador e profissional.
            - Se o usuário economizou bem, elogie e sugira um próximo passo (ex: investir).
            - Se os gastos estiverem altos, identifique a categoria principal e sugira uma redução.
            - Responda APENAS com o texto do insight. Não use prefixos como 'Savi diz:' ou 'Insight:'.

            Exemplo de resposta:
            "Sua taxa de poupança está excelente! Você economizou 35% da sua renda este mês. Que tal investir esse excedente?"
        """.trimIndent()
    )

    private fun callAiForQuickInsight(systemPrompt: SystemMessage, prompt: String): String? {
        val chatModel = OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(GPT_4_O_MINI)
            .temperature(0.2)
            .maxTokens(100)
            .build()

        val aiService = AiServices
            .builder(SaviAssistant::class.java)
            .chatLanguageModel(chatModel)
            .tools(mcpToolsConfig)
            .build()

        val messages = listOf(systemPrompt, UserMessage.from(prompt))

        return try {
            aiService.chat(messages).content().text()?.replace("\n", " ")?.trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun isValidAiInsight(insight: String?): Boolean = !insight.isNullOrBlank() && insight.length <= 150

    private fun computeFallbackInsight(summary: br.com.finsavior.monolith.finsavior_monolith.model.dto.FinancialSummary): String {
        val foreseen = summary.foreseenBalance
        val totalIncome = summary.totalBalance
        val totalExpenses = summary.totalExpenses
        val savingsRate = if (totalIncome.compareTo(java.math.BigDecimal.ZERO) == 0) java.math.BigDecimal.ZERO
        else (foreseen.divide(totalIncome, 2, java.math.RoundingMode.HALF_UP).multiply(java.math.BigDecimal(100)))

        val topCategory = summary.categoryExpenses.maxByOrNull { it.value }?.key ?: "outras despesas"

        return when {
            savingsRate >= java.math.BigDecimal(20) -> "Sua taxa de poupança está excelente! Considere investir esse excedente."
            totalExpenses >= totalIncome -> "Atenção: você gastou mais do que ganhou. Reveja gastos em $topCategory."
            else -> "Fique de olho em $topCategory; reduzir essa categoria pode aumentar sua poupança."
        }
    }

    private fun truncateInsight(text: String): String = if (text.length <= 150) text else text.take(147) + "..."

    private fun buildQuickInsightPrompt(summary: br.com.finsavior.monolith.finsavior_monolith.model.dto.FinancialSummary, accountGuide: String): String {
        val foreseen = summary.foreseenBalance
        val totalIncome = summary.totalBalance
        val totalExpenses = summary.totalExpenses
        val topCategory = summary.categoryExpenses.maxByOrNull { it.value }?.key ?: "outras despesas"

        return """
            Dados do usuário para o mês selecionado:
            - Saldo previsto: $foreseen
            - Renda total: $totalIncome
            - Gastos totais: $totalExpenses
            - Categoria com maior gasto: $topCategory

            $accountGuide

            Gere um único insight curto (máx 150 caracteres) seguindo as regras solicitadas.
        """.trimIndent()
    }


}