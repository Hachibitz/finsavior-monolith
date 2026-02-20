package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.AiProcessDocumentException
import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiBillExtractionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.DocumentProcessingHistory
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.DocumentProcessingHistoryRepository
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KLogger
import mu.KotlinLogging
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.time.LocalDateTime

@Service
class AiDocumentService(
    @param:Value("\${ai.openai.api-key}") private val apiKey: String,
    private val fsCoinService: FsCoinService,
    private val userService: UserService,
    private val documentProcessingHistoryRepository: DocumentProcessingHistoryRepository,
) {
    companion object {
        private const val OPEN_AI_CHAT_API_URL = "https://api.openai.com/v1/chat/completions"
        private const val PDF_PASSWORD_ERROR = "PASSWORD_REQUIRED"
        private const val SYSTEM_PROMPT = "Você é um contador experiente. Sua tarefa é converter extratos brutos em JSON estruturado."
    }

    private val restTemplate = RestTemplate()
    private val objectMapper = jacksonObjectMapper()
    private val log: KLogger = KotlinLogging.logger { }

    @Transactional
    fun processDocument(
        file: MultipartFile,
        docType: String,
        password: String?
    ): List<AiBillExtractionDTO> {
        val user = userService.getUserByContext()
        val userId = user.id!!

        validateImportLimit(user, userId)

        val rawText = extractTextFromPdf(file, password)
        val bills = extractFinancialData(rawText, docType)

        recordDocumentProcessing(userId, docType, isUsingCoins = false)

        return bills
    }

    @Transactional
    fun processDocumentWithCoins(
        file: MultipartFile,
        docType: String,
        password: String?
    ): List<AiBillExtractionDTO> {
        val user = userService.getUserByContext()
        val userId = user.id!!

        if (!fsCoinService.hasEnoughCoinsForDocumentImport(userId)) {
            log.error("Insufficient coins for user: $userId")
            throw InsufficientFsCoinsException("Saldo insuficiente de moedas para importar documento.")
        }

        fsCoinService.spendCoins(fsCoinService.getCoinsCostForDocumentImport(), userId)

        val rawText = extractTextFromPdf(file, password)
        val bills = extractFinancialData(rawText, docType)

        recordDocumentProcessing(userId, docType, isUsingCoins = true)

        return bills
    }

    private fun validateImportLimit(user: br.com.finsavior.monolith.finsavior_monolith.model.entity.User, userId: Long) {
        val planType = getPlanTypeById(user.userPlan!!.plan.id)
            ?: throw AiProcessDocumentException("Plano não encontrado")

        if (planType == PlanTypeEnum.FREE) {
            val currentMonthImports = countCurrentMonthImports(userId, paidWithCoins = false)
            if (currentMonthImports >= 1) {
                log.error("Free plan monthly limit exceeded for user: $userId")
                throw InsufficientFsCoinsException("Limite de importações do plano atingido. Assine ou use FSCoins para continuar.")
            }
        }
    }

    private fun countCurrentMonthImports(userId: Long, paidWithCoins: Boolean): Int {
        val now = LocalDateTime.now()
        val startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay()
        val endOfMonth = now.withDayOfMonth(now.toLocalDate().lengthOfMonth()).withHour(23).withMinute(59)

        return if (paidWithCoins) {
            documentProcessingHistoryRepository.countByUserIdAndProcessedAtBetween(userId, startOfMonth, endOfMonth)
        } else {
            documentProcessingHistoryRepository.countByUserIdAndProcessedAtBetweenAndPaidWithCoinsFalse(userId, startOfMonth, endOfMonth)
        }
    }

    private fun recordDocumentProcessing(userId: Long, docType: String, isUsingCoins: Boolean) {
        val history = DocumentProcessingHistory(
            userId = userId,
            documentType = docType,
            paidWithCoins = isUsingCoins
        )
        documentProcessingHistoryRepository.save(history)
        log.info("Document processing recorded for user: $userId, type: $docType, paidWithCoins: $isUsingCoins")
    }

    private fun extractTextFromPdf(file: MultipartFile, password: String?): String {
        return runCatching {
            PDDocument.load(file.inputStream, password ?: "").use { document ->
                PDFTextStripper().getText(document)
            }
        }.getOrElse { cause ->
            when (cause) {
                is InvalidPasswordException -> {
                    log.warn("Password-protected PDF read attempt failed: ${file.originalFilename}")
                    throw AiProcessDocumentException(PDF_PASSWORD_ERROR)
                }
                is IOException -> {
                    if (cause.message?.contains("Cannot decrypt PDF") == true) {
                        log.warn("Password-protected PDF (IOException): ${file.originalFilename}")
                        throw AiProcessDocumentException(PDF_PASSWORD_ERROR)
                    }
                    log.error(cause) { "Error reading PDF file: ${file.originalFilename}" }
                    throw AiProcessDocumentException("Erro ao ler o arquivo PDF: ${cause.message}")
                }
                else -> throw AiProcessDocumentException("Erro ao extrair texto do PDF: ${cause.message}")
            }
        }
    }

    private fun extractFinancialData(text: String, docType: String): List<AiBillExtractionDTO> {
        log.info("Sending document text for analysis. Size: ${text.length} characters")

        val headers = buildHeaders()
        val prompt = getPromptByDocType(text, docType)
        val responseContent = buildRequestBody(prompt)
            .let { requestBody -> sendOpenAiRequest(requestBody, headers) }

        log.info("AI response received. Processing JSON...")

        return parseResponseContent(responseContent)
    }

    private fun buildHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(apiKey)
    }

    private fun buildRequestBody(prompt: String): Map<String, Any> = mapOf(
        "model" to "gpt-4o-mini",
        "messages" to listOf(
            mapOf("role" to "system", "content" to SYSTEM_PROMPT),
            mapOf("role" to "user", "content" to prompt)
        ),
        "response_format" to mapOf("type" to "json_object")
    )

    private fun sendOpenAiRequest(
        requestBody: Map<String, Any>,
        headers: HttpHeaders
    ): String {
        val requestEntity = HttpEntity(requestBody, headers)

        return runCatching {
            restTemplate.postForEntity(OPEN_AI_CHAT_API_URL, requestEntity, Map::class.java)
        }.getOrElse { ex ->
            log.error(ex) { "Error calling OpenAI API" }
            throw AiProcessDocumentException("Erro ao comunicar-se com o serviço de IA.")
        }.let { response ->
            extractContentFromResponse(response)
        }
    }

    private fun extractContentFromResponse(response: org.springframework.http.ResponseEntity<Map<*, *>>): String {
        val body = response.body ?: run {
            log.error("Empty response from OpenAI API")
            throw AiProcessDocumentException("Resposta vazia da IA")
        }

        return (body["choices"] as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.firstOrNull()
            ?.let { firstChoice ->
                (firstChoice["message"] as? Map<*, *>)
                    ?.let { message -> message["content"] as? String }
                    ?: throw AiProcessDocumentException("Conteúdo da IA ausente ou com tipo inesperado.")
            }
            ?: throw AiProcessDocumentException("Resposta da IA em formato inesperado ou sem escolhas.")
    }

    private fun parseResponseContent(content: String): List<AiBillExtractionDTO> {
        return runCatching {
            val rootNode = objectMapper.readTree(content)
            val listType = objectMapper.typeFactory
                .constructCollectionType(List::class.java, AiBillExtractionDTO::class.java)

            when {
                rootNode.has("bills") -> objectMapper.readValue(rootNode.get("bills").toString(), listType)
                rootNode.isArray -> objectMapper.readValue(rootNode.toString(), listType)
                else -> {
                    log.warn("AI returned a single object outside expected pattern. Attempting conversion.")
                    listOf(objectMapper.readValue(rootNode.toString(), AiBillExtractionDTO::class.java))
                }
            }
        }.getOrElse { ex ->
            log.error(ex) { "Error interpreting document." }
            throw AiProcessDocumentException("Erro na interpretação do documento.")
        }
    }

    private fun getPromptByDocType(text: String, docType: String): String {
        val context = if (docType == "CREDIT_CARD") {
            "Este texto é uma FATURA DE CARTÃO DE CRÉDITO. Seu objetivo é extrair a LISTA DE COMPRAS."
        } else {
            "Este texto é um EXTRATO BANCÁRIO. Identifique entradas e saídas."
        }

        return """
            $context
            
            Analise o texto cru extraído do PDF abaixo e identifique todas as transações financeiras válidas.
            
            REGRAS CRÍTICAS:
            1. **IGNORE** o valor total da fatura, resumos de pagamento, juros rotativos ou linhas de cabeçalho. Eu quero APENAS as compras individuais.
            2. Se houver uma tabela de transações, itere sobre cada linha dela.
            3. Para datas, tente encontrar o ano no texto. Se não tiver ano, assuma o ano atual (2026). Formato: dd/MM/yyyy.
            4. 'billCategory': Tente categorizar (Alimentação, Transporte, Lazer, Assinaturas, etc).
            
            REGRAS DE PARCELAMENTO:
            1. Procure padrões na descrição como "01/10", "1 de 12", "3/5".
            2. Se encontrar "03/10": 
               - isInstallment: true
               - currentInstallment: 3
               - installmentCount: 10
            3. Se não mencionar parcelas, currentInstallment e installmentCount são null.
            
            IMPORTANTE: Retorne um JSON contendo uma propriedade "bills" com a lista de itens.
            Exemplo de Saída Desejada:
            {
              "bills": [
                { "billName": "Uber", "billValue": 15.90, ... },
                { "billName": "Netflix", "billValue": 39.90, ... }
              ]
            }

            ESTRUTURA DO ITEM:
            {
                "billName": "string",
                "billValue": number,
                "billDescription": "string",
                "billCategory": "string",
                "isInstallment": boolean,
                "currentInstallment": int (ou null),
                "installmentCount": int (ou null),
                "possibleDate": "dd/MM/yyyy"
            }

            TEXTO DO PDF:
            ----------------
            $text
            ----------------
        """.trimIndent()
    }
}