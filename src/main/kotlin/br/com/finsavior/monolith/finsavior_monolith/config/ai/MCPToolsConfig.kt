package br.com.finsavior.monolith.finsavior_monolith.config.ai

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ChatMessageDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ProfileDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.service.AiChatService
import br.com.finsavior.monolith.finsavior_monolith.service.BillService
import br.com.finsavior.monolith.finsavior_monolith.service.TermsAndPrivacyService
import br.com.finsavior.monolith.finsavior_monolith.service.UserService
import dev.langchain4j.agent.tool.P
import dev.langchain4j.agent.tool.Tool
import jakarta.annotation.PostConstruct
import mu.KLogger
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

@Service
class MCPToolsConfig(
    private val jdbcTemplate: JdbcTemplate,
    private val userService: UserService,
    @Lazy
    private val aiChatService: AiChatService,
    private val billService: BillService,
    private val termsAndPrivacyService: TermsAndPrivacyService
) {

    private val log: KLogger = KotlinLogging.logger {  }

    @PostConstruct
    fun verifyBeanCreation() {
        log.info(">>> MCPToolsConfig BEAN CRIADO com sucesso! <<<")
    }

    @Tool(value = ["Run arbitrary SELECT on FinSavior MySQL"])
    fun queryDatabase(
        @P("A full SQL SELECT; e.g. \"SELECT * FROM bill_table_data WHERE user_id=8 AND bill_date='May 2025'\"") sql: String
    ): List<Map<String, Any>> =
        jdbcTemplate.queryForList(sql)

    @Tool(value = ["Get user profile data (fisrtName, lastName, name, email, plan, id and username)"])
    fun getProfileData(): ProfileDataDTO =
        userService.getProfileData()

    @Tool(value = ["Load expense table data for a given month"])
    fun loadMainTableData(
        @P("Parameter billDate in 'Mmm yyyy' format (e.g. 'May 2025')") billDate: String
    ): List<BillTableDataDTO> =
        billService.loadMainTableData(billDate)

    @Tool(value = ["Load credit‐card expenses for a given month"])
    fun loadCardTableData(
        @P("Parameter billDate in 'Mmm yyyy' format (e.g. 'May 2025')") billDate: String
    ): List<BillTableDataDTO> =
        billService.loadCardTableData(billDate)

    @Tool(value = ["Load assets (salary, bonuses, etc) for a given month"])
    fun loadAssetsTableData(
        @P("Parameter billDate in 'Mmm yyyy' format (e.g. 'May 2025')") billDate: String
    ): List<BillTableDataDTO> =
        billService.loadAssetsTableData(billDate)

    @Tool(value = ["Load credit‐card payments (invoice payments) for a given month"])
    fun loadPaymentCardTableData(
        @P("Parameter billDate in 'Mmm yyyy' format (e.g. 'May 2025')") billDate: String
    ): List<BillTableDataDTO> =
        billService.loadPaymentCardTableData(billDate)

    @Tool(value = ["Delete a bill item by its ID"])
    fun deleteBillItem(@P("itemId from bill_table_data") itemId: Long): String {
        billService.deleteItemFromTable(itemId)
        return "OK"
    }

    @Tool(value = ["Edit a bill item"])
    fun editBillItem(
        @P("request containing the data to update the register in bill_table_data") request: BillTableDataDTO
    ): String {
        billService.billUpdate(request)
        return "OK"
    }

    @Tool(value = ["Fetch user chat history (offset, limit)"])
    fun getChatHistory(
        @P("offset") offset: Int,
        @P("limit") limit: Int
    ): List<ChatMessageDTO> {
        val userId = userService.getUserByContext().id!!
        return aiChatService.getUserChatHistoryDTO(userId, offset, limit)
    }

    @Tool(value = ["Get official app information and features"])
    fun getAppInfo(): Map<String, Any> = mapOf(
        "description" to """
            O FinSavior é um app de **gerenciamento financeiro pessoal com inteligência artificial**. 
            Ele foi criado para simplificar o controle de gastos, receitas, metas e decisões financeiras, 
            com ajuda da IA (você! 👋).
        """.trimIndent(),
        "features" to listOf(
            "Assistente inteligente (Savi)",
            "Análises automáticas",
            "Controle de gastos e receitas",
            "Relatórios personalizados"
        ),
        "more_info" to "Acesse Menu > Sobre o FinSavior"
    )

    @Tool(value = ["Get developer information"])
    fun getDevInfo(): Map<String, String> = mapOf(
        "creator" to "Hachibitz - Felipe Almeida (Desenvolvedor Solo)",
        "story" to """
            O app nasceu de um dev solo (apelido: **Hachibitz**) que começou tudo com... uma planilha do Excel 😅. 
            Ele sentiu que precisava de algo mais poderoso pra gerenciar suas finanças, e foi aí que o FinSavior nasceu — 
            com muito carinho e café. ☕
            
            Ele cuidou de tudo: front, back, design, testes, segurança... tudo mesmo! Com a chegada da IA, ele viu a chance 
            de transformar o app em algo mais potente e útil pra todo mundo. E decidiu: *“Por que não ajudar outras pessoas também?”*
        """.trimIndent(),
        "contact" to "https://www.linkedin.com/in/felipe-almeida-dev/",
        "more_info" to "Acesse Menu > Sobre o dev"
    )

    @Tool(value = ["Get current plan features and limits"])
    fun getPlanDetails(@P("planType from user profile") planType: String): Map<String, Any> {
        val plan = PlanTypeEnum.valueOf(planType)
        return mapOf(
            "name" to plan.name,
            "features" to listOf(
                "${plan.amountOfMonthAnalysisPerMonth} análises mensais",
                "${plan.amountOfTrimesterAnalysisPerMonth} análises trimestrais",
                "${plan.amountOfAnnualAnalysisPerMonth} análises anuais",
                "${plan.amountOfChatMessagesWithSavi} mensagens com a Savi por mês",
                "Até ${if (plan.maxTokensPerMonth == Int.MAX_VALUE) "ilimitados" else plan.maxTokensPerMonth} tokens de IA por mês"
            ),
            "limits" to mapOf(
                "mensagens" to plan.amountOfChatMessagesWithSavi,
                "tokens" to if (plan.maxTokensPerMonth == Int.MAX_VALUE) "ilimitados" else plan.maxTokensPerMonth
            )
        )
    }

    @Tool(value = ["Get terms of service"])
    fun getTerms(): String = termsAndPrivacyService.getTerms()

    @Tool(value = ["Get privacy policy"])
    fun getPrivacyPolicy(): String = termsAndPrivacyService.getPrivacyPolicy()
}