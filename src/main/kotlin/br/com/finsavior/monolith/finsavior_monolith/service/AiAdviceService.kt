package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.ai.MCPToolsConfig
import br.com.finsavior.monolith.finsavior_monolith.exception.AiAdviceException
import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class AiAdviceService(
    private val aiAdviceRepository: AiAdviceRepository,
    private val analysisHistoryRepository: AiAnalysisHistoryRepository,
    @Lazy private val userService: UserService,
    @Value("\${ai.openai.api-key}") private val openAiApiKey: String,
    private val fsCoinService: FsCoinService
) {

    @Autowired
    lateinit var mcpToolsConfig: MCPToolsConfig

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
}