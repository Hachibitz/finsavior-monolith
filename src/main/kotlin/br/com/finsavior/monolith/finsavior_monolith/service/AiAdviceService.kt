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
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PromptEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toAiAnalysisDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.AiAdviceRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.AnalysisHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanRepository
import org.springframework.ai.chat.ChatClient
import org.springframework.ai.chat.ChatResponse
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.*

@Service
class AiAdviceService(
    private val chatClient: ChatClient,
    private val aiAdviceRepository: AiAdviceRepository,
    private val analysisHistoryRepository: AnalysisHistoryRepository,
    private val promptConfig: PromptConfig,
    private val planRepository: PlanRepository,
    @Lazy private val userService: UserService
) {

    fun getAiAdviceById(aiAdviceId: Long): AiAnalysisDTO =
        aiAdviceRepository.findById(aiAdviceId)
            .orElseThrow { AiAdviceException("Análise não encontrada") }.toAiAnalysisDTO()

    fun generateAiAdviceAndInsights(request: AiAdviceDTO): AiAdviceResponseDTO {
        val currentDateTime = LocalDateTime.now()
        val user: User = userService.getUserByContext()

        val analysisType = getAnalysisTypeById(request.analysisTypeId) ?:
            throw AiAdviceException("Tipo de análise não encontrada")
        val planType: PlanTypeEnum = getPlanTypeById(user.userPlan!!.plan.id) ?:
            throw AiAdviceException("Plano não encontrado")

        val hasUsedFreeAnalysis = aiAdviceRepository.existsByUserIdAndIsFreeAnalysisTrue(user.id!!)
        if (!validatePlanAndAnalysisType(user, analysisType, planType, hasUsedFreeAnalysis)) {
            throw AiAdviceException("Consulta excedida pelo plano")
        }

        request.prompt = getPrompt(request)
        if (request.prompt.isNullOrBlank()) {
            throw AiAdviceException("O prompt não pode ser nulo ou vazio")
        }

        val messages = mutableListOf<Message>()
        messages.add(UserMessage(request.prompt))

        val options = OpenAiChatOptions.builder()
            .withModel("gpt-3.5-turbo-1106")
            .withUser("FinSaviorApp")
            .withTemperature(
                if (planType == PlanTypeEnum.FREE) 0.0f
                else request.temperature
            )
            .withMaxTokens(
                when (analysisType) {
                    AnalysisTypeEnum.TRIMESTER -> 3000
                    AnalysisTypeEnum.ANNUAL -> 6000
                    else -> 1000
                }
            )
            .build()

        val chatResponse = try {
            chatClient.call(
                Prompt(
                    messages,
                    options
                )
            )
        } catch (e: Exception) {
            throw AiAdviceException("Falha na comunicação com a API de IA", e)
        }

        val isFree = planType == PlanTypeEnum.FREE && !hasUsedFreeAnalysis
        val advice = saveAiAdvice(user.id!!, request, chatResponse, currentDateTime, isFree)

        return AiAdviceResponseDTO(advice.id!!)
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
                if (trimesterAiAdvicesOfMonth >= planType.amountOfTrimesterAnalysisPerMonth) {
                    return false
                }
                if (monthAiAdvicesOfMonth >= planType.amountOfMonthAnalysisPerMonth) {
                    return false
                }
            }

            AnalysisTypeEnum.TRIMESTER -> {
                if (trimesterAiAdvicesOfMonth >= planType.amountOfTrimesterAnalysisPerMonth) {
                    return false
                }
                if (monthAiAdvicesOfMonth >= planType.amountOfMonthAnalysisPerMonth) {
                    return false
                }
            }

            AnalysisTypeEnum.MONTH -> if (monthAiAdvicesOfMonth >= planType.amountOfMonthAnalysisPerMonth) {
                return false
            }
        }

        return true
    }

    private fun getPlanTypeById(planTypeId: String): PlanTypeEnum? =
        Arrays.stream(PlanTypeEnum.entries.toTypedArray())
            .filter { planType -> planType.id == planTypeId }
            .findFirst()
            .orElse(null)

    private fun getAnalysisTypeById(analysisTypeId: Int?): AnalysisTypeEnum? =
        Arrays.stream(AnalysisTypeEnum.entries.toTypedArray())
            .filter { analysis -> analysis.analysisTypeId == analysisTypeId }
            .findFirst()
            .orElse(null)

    private fun getPrompt(aiAdviceDTO: AiAdviceDTO): String {
        val chosenAnalysis: AnalysisTypeEnum = getChosenAnalysis(aiAdviceDTO)
        val promptParts: List<String> = getPromptByAnalysisType(chosenAnalysis).getPromptParts(promptConfig)
        return getFormattedPrompt(promptParts, aiAdviceDTO)
    }

    private fun getPromptByAnalysisType(analysisType: AnalysisTypeEnum?): PromptEnum =
        PromptEnum.entries.find { it.analysisType == analysisType }
            ?: throw IllegalArgumentException("Tipo de prompt não encontrado")

    private fun getFormattedPrompt(promptParts: List<String>, aiAdvice: AiAdviceDTO): String {
        val prompt = StringBuilder()
        prompt.append(promptParts[0])
            .append(aiAdvice.mainAndIncomeTable).append("\n\n")
            .append(promptParts[1]).append("\n\n")
            .append(aiAdvice.cardTable).append("\n\n")
            .append(promptParts[2])
            .append(promptParts[3])

        return prompt.toString()
    }

    private fun validateAnalysisTypeAndPlan(chosenAnalysis: AnalysisTypeEnum, user: User): Boolean {
        return chosenAnalysis.plansCoverageList.stream()
            .anyMatch { planType -> planType.id == user.userPlan!!.plan.id }
    }

    private fun getChosenAnalysis(aiAdvice: AiAdviceDTO): AnalysisTypeEnum {
        val chosenAnalysis: AnalysisTypeEnum = checkNotNull(
            Arrays.stream(AnalysisTypeEnum.entries.toTypedArray())
                .filter(({ type -> type.analysisTypeId == aiAdvice.analysisTypeId }))
                .findFirst()
                .orElse(null)
        )

        return chosenAnalysis
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
                prompt = getPrompt(request),
                resultMessage = chatResponse.result.output.content,
                analysisTypeId = request.analysisTypeId,
                temperature = request.temperature,
                date = generatedAt,
                startDate = request.startDate,
                finishDate = request.finishDate,
                isFreeAnalysis = isFree,
                audit = Audit()
            )
        )

        analysisHistoryRepository.save(
            AiAnalysisHistory(
                userId = userId,
                analysisTypeId = request.analysisTypeId,
                date = generatedAt,
                audit = Audit()
            )
        )

        return aiAdvice
    }
}