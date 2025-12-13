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

        try {
            log.info("M=transcribeAudio, I=Enviando MP3 convertido para OpenAI.")
            val requestEntity = createOpenAiTranscriptionRequest(mp3File)
            val response = restTemplate.postForEntity(OPEN_AI_TRANSCRIBE_AUDIO_API_URL, requestEntity, Map::class.java)
            val transcription = extractTranscriptionFromResponse(response.body)

            log.info("M=transcribeAudio, I=Transcrição de áudio bem-sucedida.")
            return transcription
        } catch (e: Exception) {
            log.error("M=transcribeAudio, E=Erro na chamada da OpenAI.", e)
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

    /**
     * Pega o MultipartFile, salva em disco temporariamente,
     * converte para MP3 usando FFmpeg (JAVE2) e retorna o arquivo MP3.
     */
    private fun convertToMp3(sourceFile: MultipartFile): File {
        val sourceTemp = File.createTempFile("upload_", "_src")
        val targetTemp = File.createTempFile("convert_", ".mp3")

        try {
            sourceFile.transferTo(sourceTemp)

            val audioAttributes = createAudioAttributes()
            val encodingAttributes = createEncodingAttributes(audioAttributes)

            Encoder().encode(MultimediaObject(sourceTemp), targetTemp, encodingAttributes)

            log.info("M=convertToMp3, I=Conversão realizada com sucesso: ${targetTemp.absolutePath}")
            return targetTemp

        } catch (e: Exception) {
            log.error("M=convertToMp3, E=Erro na conversão de áudio.", e)
            throw RuntimeException("Falha ao converter áudio para MP3: ${e.message}")
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

    private fun extractDataFromText(text: String): AiBillExtractionDTO {
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

        return """
            Você é um assistente financeiro. Hoje é $today.
            Analise o texto do usuário e extraia os dados para uma conta.
            
            REGRAS:
            1. Se o usuário quiser "conversar", "falar com a Savi", "tirar dúvida", "abrir chat" ou algo que não seja adicionar uma conta, retorne o JSON com "redirectAction": "CHAT_SAVI".
            2. Caso contrário, extraia os dados da conta.
            3. Se o usuário mencionar que parcelou, retorne o billValue com o valor da parcela e não com o valor total da compra, o valor total da compra deve ser inserido na descrição.
            
            Retorne APENAS um JSON estrito (sem markdown) neste formato:
            {
                "billName": "string (curto, resumido)",
                "billValue": number,
                "billDescription": "string (o texto original)",
                "billCategory": "string (tente adivinhar: Alimentação, Transporte, Lazer, Saúde, Outras)",
                "isInstallment": boolean,
                "installmentCount": int (se não mencionado, null),
                "isRecurrent": boolean (se falar 'todo mês' ou 'fixo'),
                "possibleDate": "string (dd/MM/yyyy)",
                "redirectAction": "string (CHAT_SAVI ou null)"
            }
            
            Texto: "$text"
        """.trimIndent()
    }
}