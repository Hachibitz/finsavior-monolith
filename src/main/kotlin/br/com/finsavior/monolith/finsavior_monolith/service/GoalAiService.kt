package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.AiAdviceException
import br.com.finsavior.monolith.finsavior_monolith.exception.GoalNotFoundException
import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GoalAdviceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Goal
import br.com.finsavior.monolith.finsavior_monolith.model.entity.GoalAdviceHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.GoalAdviceHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.GoalRepository
import br.com.finsavior.monolith.finsavior_monolith.service.strategy.SaviAssistant
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.openai.OpenAiChatModel
import dev.langchain4j.model.openai.OpenAiChatModelName
import dev.langchain4j.service.AiServices
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class GoalAiService(
    private val goalRepository: GoalRepository,
    private val goalAdviceHistoryRepository: GoalAdviceHistoryRepository,
    private val userService: UserService,
    private val fsCoinService: FsCoinService,
    @param:Value("\${ai.openai.api-key}") private val openAiApiKey: String,
    @param:Value("\${fscoins-cost-for-goal-advice:5}") private val coinsCostForGoalAdvice: Long
) {

    fun getGoalAdvice(goalId: String, useCoins: Boolean): Map<String, String> {
        val user = userService.getUserByContext()
        val goal = goalRepository.findById(goalId)
            .orElseThrow { GoalNotFoundException("Meta com id $goalId não encontrada") }

        validateGoalAdvicePlanCoverage(user, useCoins)

        val prompt = buildGoalAdvicePrompt(goal)

        val chatModel = OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(OpenAiChatModelName.GPT_4_O_MINI)
            .temperature(0.7)
            .build()

        val aiService = AiServices.builder(SaviAssistant::class.java)
            .chatLanguageModel(chatModel)
            .build()

        val systemMessage = SystemMessage.from("Você é um consultor financeiro. Dê conselhos diretos e motivacionais.")
        val userMessage = UserMessage.from(prompt)

        val response = aiService.chat(listOf(systemMessage, userMessage)).content().text()

        val adviceToSave = if (response.isNullOrBlank()) {
            "Não foi possível gerar um conselho neste momento. Tente novamente mais tarde."
        } else {
            response
        }

        goalAdviceHistoryRepository.save(GoalAdviceHistory(user = user, goal = goal, usedCoins = useCoins, advice = adviceToSave))

        return mapOf("advice" to adviceToSave)
    }

    fun getAdvicesForGoal(goalId: String): List<GoalAdviceDTO> {
        val user = userService.getUserByContext()
        return goalAdviceHistoryRepository.findByGoalIdAndUserId(goalId, user.id!!).map { it.toDTO() }
    }

    private fun buildGoalAdvicePrompt(goal: Goal): String {
        return """
            Dê-me um conselho para alcançar minha meta:
            - Nome da meta: ${goal.name}
            - Valor alvo: ${goal.targetAmount}
            - Valor atual: ${goal.currentAmount}
            - Prazo: ${goal.deadline}
            - Categoria: ${goal.category ?: "N/A"}
            
            Seja direto, motivacional e ofereça dicas práticas.
            **Responda sempre em MarkDown!**
        """.trimIndent()
    }

    private fun validateGoalAdvicePlanCoverage(user: User, useCoins: Boolean) {
        val plan = CommonUtils.getPlanTypeById(user.userPlan!!.plan.id) ?: PlanTypeEnum.FREE
        val userId = user.id!!

        if (useCoins) {
            if (fsCoinService.getBalance(userId) < coinsCostForGoalAdvice) {
                throw InsufficientFsCoinsException("Saldo de FS Coins insuficiente.")
            }
            fsCoinService.spendCoins(coinsCostForGoalAdvice, userId)
        } else {
            if (plan.maxGoalAdvicesPerMonth == Int.MAX_VALUE) return

            val startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
            val endOfMonth = LocalDateTime.now()
            val usageCount = goalAdviceHistoryRepository.countByUserIdAndCreatedAtBetweenAndUsedCoinsIsFalse(userId, startOfMonth, endOfMonth)

            if (usageCount >= plan.maxGoalAdvicesPerMonth) {
                throw AiAdviceException("Limite mensal de conselhos para metas atingido. Use FS Coins ou faça upgrade do seu plano.")
            }
        }
    }
}
