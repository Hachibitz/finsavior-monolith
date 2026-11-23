package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.config.ai.ChatModelFactory
import br.com.finsavior.monolith.finsavior_monolith.config.ai.MCPToolsConfig
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiChatRequest
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageEntity
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ChatMessageHistory
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Plan
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserPlan
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.ChatMessageHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.ChatMessageRepository
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.output.TokenUsage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.YearMonth

class AiChatServiceTest {
    @Test
    fun `sends historical roles and selected month to the model`() {
        val users = mock<UserService>()
        val messages = mock<ChatMessageRepository>()
        val model = mock<ChatLanguageModel>()
        val modelFactory = mock<ChatModelFactory>()
        whenever(modelFactory.forPlan(any())).thenReturn(model)
        val tools = mock<MCPToolsConfig>()
        val user = User().copy(id = 1, userPlan = UserPlan(1, Plan("1L", PlanTypeEnum.FREE)))
        whenever(users.getUserByContext()).thenReturn(user)
        val longAnswer = "A".repeat(1600) + " Quer que eu monte um plano?"
        whenever(messages.findByUserIdOrderByCreatedAtDesc(any(), any())).thenReturn(
            listOf(ChatMessageEntity(userId = 1, userMessage = "Como economizar?", assistantResponse = longAnswer))
        )
        var sent: ChatRequest? = null
        whenever(model.chat(any<ChatRequest>())).thenAnswer {
            sent = it.getArgument(0)
            ChatResponse.builder().aiMessage(AiMessage.from("Plano")).tokenUsage(TokenUsage(100, 10)).build()
        }
        val service = AiChatService(users, messages, mock<ChatMessageHistoryRepository>(), modelFactory, tools, mock<FsCoinService>(), 10)

        service.askQuestion(1, "Sim, por favor", "pt-BR", YearMonth.of(2026, 12))

        val history = requireNotNull(sent).messages()
        assertEquals(4, history.size)
        assertTrue((history[0] as SystemMessage).text().contains("2026-12"))
        assertEquals("Como economizar?", (history[1] as UserMessage).singleText())
        assertTrue((history[2] as AiMessage).text().endsWith("Quer que eu monte um plano?"))
        assertEquals("Sim, por favor", (history[3] as UserMessage).singleText())
    }

    @Test
    fun `coin-funded chat marks paidWithCoins true and queries plan usage`() {
        val users = mock<UserService>()
        val messages = mock<ChatMessageRepository>()
        val historyRepo = mock<ChatMessageHistoryRepository>()
        val model = mock<ChatLanguageModel>()
        val modelFactory = mock<ChatModelFactory>()
        whenever(modelFactory.forPlan(any())).thenReturn(model)
        val tools = mock<MCPToolsConfig>()
        val coins = mock<FsCoinService>()

        val user = User().copy(id = 7, userPlan = UserPlan(7, Plan("1L", PlanTypeEnum.FREE)))
        whenever(users.getUserByContext()).thenReturn(user)
        whenever(coins.getBalance(7L)).thenReturn(50L)
        whenever(messages.findByUserIdOrderByCreatedAtDesc(eq(7L), any())).thenReturn(emptyList())
        whenever(messages.save(any<ChatMessageEntity>())).thenAnswer {
            it.getArgument<ChatMessageEntity>(0).copy(id = 99L)
        }
        whenever(historyRepo.save(any<ChatMessageHistory>())).thenAnswer { it.getArgument<ChatMessageHistory>(0) }
        whenever(model.chat(any<ChatRequest>())).thenReturn(
            ChatResponse.builder().aiMessage(AiMessage.from("Ok")).tokenUsage(TokenUsage(50, 20)).build()
        )

        val service = AiChatService(users, messages, historyRepo, modelFactory, tools, coins, 10L)
        service.chatWithAssistant(AiChatRequest(question = "Oi", isUsingCoins = true))

        val captor = argumentCaptor<ChatMessageHistory>()
        verify(historyRepo).save(captor.capture())
        assertEquals(true, captor.firstValue.paidWithCoins)
        assertEquals(70L, captor.firstValue.tokensUsed)
    }
}
