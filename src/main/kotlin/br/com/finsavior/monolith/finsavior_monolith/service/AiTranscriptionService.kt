package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperApiException
import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperLimitException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiBillExtractionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.enums.AudioProcessingStatus
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KLogger
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import ws.schild.jave.Encoder
import ws.schild.jave.MultimediaObject
import ws.schild.jave.encode.AudioAttributes
import ws.schild.jave.encode.EncodingAttributes
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class AiTranscriptionService(
    @param:Value("\${ai.openai.api-key}") private val apiKey: String,
    private val userService: UserService,
    private val audioProcessingHistoryService: AudioProcessingHistoryService,
    private val fsCoinService: FsCoinService,
    private val categoryService: CategoryService,
    @param:Value("\${fscoins-cost-for-audio:10}") private val coinsCostForAudio: Long,
) {
    companion object {
        const val OPEN_AI_TRANSCRIBE_AUDIO_API_URL = "https://api.openai.com/v1/audio/transcriptions"
        const val OPEN_AI_CHAT_COMPLETION_API_URL = "https://api.openai.com/v1/chat/completions"
    }

    private val restTemplate = RestTemplate()
    private val objectMapper = jacksonObjectMapper()
    private val log: KLogger = KotlinLogging.logger {  }

    fun processAudioToBill(audioFile: MultipartFile, isUsingCoins: Boolean): AiBillExtractionDTO {
        val userId = userService.getUserByContext().id!!

        if (isUsingCoins) {
            val balance = fsCoinService.getBalance(userId)
            validateUsingCoins(userId, balance)
            useCoinsForAudio(userId)
        }

        val historyRecord = audioProcessingHistoryService.reserveAudioUsage(isUsingCoins)

        try {
            val transcription = transcribeAudio(audioFile)
            val resultDTO = extractDataFromText(transcription)

            log.info("M=processAudioToBill, I=Transcrição bem-sucedida.")
            audioProcessingHistoryService.updateUsageStatus(historyRecord, AudioProcessingStatus.SUCCESS)
            return resultDTO
        } catch (e: Exception) {
            log.error("M=processAudioToBill, E=Erro ao processar áudio.", e)
            audioProcessingHistoryService.updateUsageStatus(historyRecord, AudioProcessingStatus.ERROR)
            throw e
        }
    }

    fun transcribeOnly(audioFile: MultipartFile, isUsingCoins: Boolean): String {
        validateAudioProcessingLimit(isUsingCoins)
        return transcribeAudio(audioFile)
    }

    private fun transcribeAudio(audioFile: MultipartFile): String {
        log.info("M=transcribeAudio, I=Iniciando processo. Arquivo recebido: ${audioFile.originalFilename}, Tamanho: ${audioFile.size}")

        val mp3File = convertToMp3(audioFile)
        return sendToOpenAI(mp3File)
    }

    fun transcribeAudioFromFile(audioFile: File): String {
        log.info("M=transcribeAudioFromFile, I=Iniciando processo. Arquivo recebido: ${audioFile.name}, Tamanho: ${audioFile.length()}")

        val mp3File = convertToMp3(audioFile)
        try {
            return sendToOpenAI(mp3File)
        } finally {
            try {
                Files.deleteIfExists(audioFile.toPath())
            } catch (ex: Exception) {
                log.warn("Falha ao deletar arquivo de áudio original: ${audioFile.absolutePath}")
            }
        }
    }

    private fun sendToOpenAI(mp3File: File): String {
        try {
            log.info("M=sendToOpenAI, I=Enviando MP3 convertido para OpenAI.")
            val requestEntity = createOpenAiTranscriptionRequest(mp3File)
            val response = restTemplate.postForEntity(OPEN_AI_TRANSCRIBE_AUDIO_API_URL, requestEntity, Map::class.java)
            val transcription = extractTranscriptionFromResponse(response.body)

            log.info("M=sendToOpenAI, I=Transcrição de áudio bem-sucedida.")
            return transcription
        } catch (e: Exception) {
            log.error("M=sendToOpenAI, E=Erro na chamada da OpenAI.", e)
            throw WhisperApiException("Erro na transcrição: ${e.message}", e)
        } finally {
            try {
                Files.deleteIfExists(mp3File.toPath())
            } catch (ex: Exception) {
                log.warn("Falha ao deletar arquivo temporário: ${mp3File.absolutePath}")
            }
        }
    }

    private fun createOpenAiTranscriptionRequest(mp3File: File): HttpEntity<LinkedMultiValueMap<String, Any>> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        headers.setBearerAuth(apiKey)

        val body = LinkedMultiValueMap<String, Any>().apply {
            add("file", FileSystemResource(mp3File))
            add("model", "whisper-1")
            add("language", "pt")
        }
        return HttpEntity(body, headers)
    }

    private fun extractTranscriptionFromResponse(responseBody: Map<*, *>?): String {
        return responseBody?.get("text") as? String
            ?: throw WhisperApiException("Resposta da OpenAI não contém o campo 'text'.")
    }

    private fun convertToMp3(sourceFile: File): File {
        val targetTemp = File.createTempFile("convert_", ".mp3")
        try {
            val audioAttributes = createAudioAttributes()
            val encodingAttributes = createEncodingAttributes(audioAttributes)

            Encoder().encode(MultimediaObject(sourceFile), targetTemp, encodingAttributes)

            log.info("M=convertToMp3, I=Conversão realizada com sucesso: ${targetTemp.absolutePath}")
            return targetTemp

        } catch (e: Exception) {
            log.error("M=convertToMp3, E=Erro na conversão de áudio.", e)
            throw RuntimeException("Falha ao converter áudio para MP3: ${e.message}")
        }
    }

    private fun convertToMp3(sourceFile: MultipartFile): File {
        val sourceTemp = File.createTempFile("upload_", "_src")
        try {
            sourceFile.transferTo(sourceTemp)
            return convertToMp3(sourceTemp)
        } finally {
            try {
                Files.deleteIfExists(sourceTemp.toPath())
            } catch (ex: Exception) {
                log.warn("Falha ao deletar temp source: ${sourceTemp.absolutePath}")
            }
        }
    }

    private fun createAudioAttributes(): AudioAttributes {
        return AudioAttributes().apply {
            setCodec("libmp3lame")
            setBitRate(128000)
            setChannels(1)
            setSamplingRate(44100)
        }
    }

    private fun createEncodingAttributes(audioAttributes: AudioAttributes): EncodingAttributes {
        return EncodingAttributes().apply {
            setOutputFormat("mp3")
            setAudioAttributes(audioAttributes)
        }
    }

    fun extractDataFromText(text: String): AiBillExtractionDTO {
        log.info("M=extractDataFromText, I=Iniciando extração de dados do texto.")
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.setBearerAuth(apiKey)

        val prompt = getBillFromWhisperPrompt(text)

        val requestBody = mapOf(
            "model" to "gpt-4o-mini",
            "messages" to listOf(
                mapOf("role" to "system", "content" to "Retorne apenas JSON."),
                mapOf("role" to "user", "content" to prompt)
            ),
            "response_format" to mapOf("type" to "json_object")
        )

        val requestEntity = HttpEntity(requestBody, headers)

        try {
            val response = restTemplate.postForEntity(OPEN_AI_CHAT_COMPLETION_API_URL, requestEntity, Map::class.java)
            val choices = response.body?.get("choices") as List<Map<String, Any>>
            val message = choices[0]["message"] as Map<String, Any>
            val content = message["content"] as String
            val extractedData = objectMapper.readValue(content, AiBillExtractionDTO::class.java)
            log.info("M=extractDataFromText, I=Extração de dados do texto bem-sucedida.")
            return extractedData
        } catch (e: Exception) {
            log.error("M=extractDataFromText, E=Erro na interpretação do texto.", e)
            throw RuntimeException("Erro na interpretação: ${e.message}")
        }
    }

    private fun validateAudioProcessingLimit(isUsingCoins: Boolean) {
        val user = userService.getUserByContext()
        val userId = user.id!!
        val plan = getPlanTypeById(user.userPlan!!.plan.id)

        log.info("M=validateAudioLimit, I=Iniciando validação de limite de áudio.")
        if ((plan?.maxAudioBillEntries ?: 0) > 1000) return

        if (isUsingCoins) {
            log.info("M=validateAudioLimit, I=Usando FSCoins para validar limite de áudio.")
            val balance = fsCoinService.getBalance(userId)
            validateUsingCoins(userId, balance)
            useCoinsForAudio(userId)
            log.info("M=validateAudioLimit, I=FSCoins validados com sucesso.")
        } else {
            val usageCount = audioProcessingHistoryService.countAudioEntriesCurrentMonthFree(userId)
            log.info("M=validateAudioLimit, I=Entradas de áudio no mês atual: $usageCount.")

            if (usageCount >= plan!!.maxAudioBillEntries) {
                log.warn("M=validateAudioLimit, W=Limite do plano atingido para o usuário $userId.")
                throw WhisperLimitException("Limite mensal de áudio atingido (${plan.maxAudioBillEntries}/mês). Faça upgrade para ilimitado!")
            }
        }
    }

    private fun validateUsingCoins(userId: Long, fsCoinBalance: Long) {
        log.info("Validating coins usage for user: $userId")
        if (fsCoinBalance < coinsCostForAudio) {
            log.error("Insufficient coins for user: $userId")
            throw InsufficientFsCoinsException("Saldo insuficiente de moedas.")
        }
    }

    private fun useCoinsForAudio(userId: Long) {
        fsCoinService.spendCoins(coinsCostForAudio, userId)
        log.info("Deducted $coinsCostForAudio coins for user: $userId for audio processing")
    }

    private fun getBillFromWhisperPrompt(text: String): String {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        val categories = categoryService.listCategories().joinToString(", ") { it.name }

        return """
            Você é um assistente financeiro especialista em extrair dados de texto. Hoje é $today.
            Analise o texto do usuário e extraia os dados para uma conta, preenchendo o JSON abaixo.

            REGRAS DE EXTRAÇÃO:

            1.  **Redirecionamento para Chat**:
                *   Se o usuário quiser "conversar", "falar com a Savi", "tirar dúvida", "abrir chat" ou algo que não seja adicionar uma conta, retorne o JSON com "redirectAction": "CHAT_SAVI".

            2.  **Extração de Dados da Conta**:
                *   **billTable**:
                    *   `CREDIT_CARD`: Se o usuário mencionar "crédito", "cartão", "vencimento", "fatura" ou "parcelado".
                        *   Exemplo: "comprei dois pastéis no crédito, foi 50 reais" -> "billTable": "CREDIT_CARD"
                    *   `ASSETS`: Se for uma entrada de dinheiro, como "salário", "recebi", "ganhei", "bônus".
                        *   Exemplo: "Recebi meu salário, 8000 reais" -> "billTable": "ASSETS"
                    *   `PAYMENT_CARD`: Se for um pagamento de fatura de cartão.
                        *   Exemplo: "paguei a fatura do cartão, 500 reais" -> "billTable": "PAYMENT_CARD"
                    *   `MAIN`: Para todas as outras despesas.
                        *   Exemplo: "comprei um café, 5 reais" -> "billTable": "MAIN"
                *   **isRecurrent**:
                    *   `true`: Se o usuário mencionar "todo mês", "fixo", "recorrente", "mensal".
                        *   Exemplo: "adiciona uma conta fixa todos os meses, aluguel, mil reais" -> "isRecurrent": true
                *   **Parcelamento (isInstallment e installmentCount)**:
                    *   Se o usuário mencionar "parcelado", "vezes", "x" (no sentido de multiplicação).
                    *   `isInstallment`: true
                    *   `installmentCount`: o número de parcelas.
                    *   `billValue`: **O VALOR DA PARCELA**, não o valor total. O valor total deve ir na descrição.
                        *   Exemplo: "Comprei um celular de 2000 reais em 10x" -> "isInstallment": true, "installmentCount": 10, "billValue": 200.00, "billDescription": "Celular, valor total 2000.00"

            Retorne APENAS um JSON estrito (sem markdown) neste formato:
            {
                "billName": "string (curto, resumido)",
                "billValue": number,
                "billDescription": "string (o texto original)",
                "billCategory": "string (tente adivinhar pelas opções a seguir: $categories)",
                "billTable": "string (MAIN, CREDIT_CARD, ASSETS, ou PAYMENT_CARD)",
                "isInstallment": boolean,
                "installmentCount": int (se não mencionado, null),
                "currentInstallment": int (sempre 1 se for um novo parcelamento, ou null se não for parcelado),
                "isRecurrent": boolean (se não mencionado, false),
                "possibleDate": "string (dd/MM/yyyy)",
                "redirectAction": "string (CHAT_SAVI ou null)"
            }

            Texto: "$text"
        """.trimIndent()
    }
}