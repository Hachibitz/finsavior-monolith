package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperApiException
import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperLimitException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiBillExtractionDTO
import br.com.finsavior.monolith.finsavior_monolith.util.CommonUtils.Companion.getPlanTypeById
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import mu.KLogger
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class AiTranscriptionService(
    @param:Value("\${ai.openai.api-key}") private val apiKey: String,
    private val userService: UserService,
    private val billService: BillService,
) {
    companion object {
        const val OPEN_AI_TRANSCRIBE_AUDIO_API_URL = "https://api.openai.com/v1/audio/transcriptions"
        const val OPEN_AI_CHAT_COMPLETION_API_URL = "https://api.openai.com/v1/chat/completions"
    }

    private val restTemplate = RestTemplate()
    private val objectMapper = jacksonObjectMapper()
    private val log: KLogger = KotlinLogging.logger {  }

    fun processAudioToBill(audioFile: MultipartFile): AiBillExtractionDTO {
        log.info("M=processAudioToBill, I=Iniciando processamento de áudio para conta.")
        val transcription = transcribeAudio(audioFile)
        log.info("M=processAudioToBill, I=Transcrição do áudio realizada com sucesso.")

        try {
            validateAudioLimit()
        } catch (e: Exception) {
            log.error("M=processAudioToBill, E=Limite do plano atingido.", e)
            throw WhisperLimitException(e.message ?: "Limite do plano FREE atingido: 2 contas via áudio por mês.", e)
        }

        val extractedData = extractDataFromText(transcription)
        log.info("M=processAudioToBill, I=Extração de dados do texto realizada com sucesso.")
        return extractedData
    }

    private fun transcribeAudio(audioFile: MultipartFile): String {
        log.info("M=transcribeAudio, I=Iniciando transcrição de áudio.")
        val headers = HttpHeaders()
        headers.contentType = MediaType.MULTIPART_FORM_DATA
        headers.setBearerAuth(apiKey)

        val body = LinkedMultiValueMap<String, Any>()
        body.add("file", object : ByteArrayResource(audioFile.bytes) {
            override fun getFilename(): String = "audio.m4a"
        })
        body.add("model", "whisper-1")
        body.add("language", "pt")

        val requestEntity = HttpEntity(body, headers)

        try {
            val response = restTemplate.postForEntity(OPEN_AI_TRANSCRIBE_AUDIO_API_URL, requestEntity, Map::class.java)
            val transcription = response.body?.get("text") as String
            log.info("M=transcribeAudio, I=Transcrição de áudio bem-sucedida.")
            return transcription
        } catch (e: Exception) {
            log.error("M=transcribeAudio, E=Erro na transcrição de áudio.", e)
            throw WhisperApiException("Erro na transcrição: ${e.message}", e)
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

    private fun validateAudioLimit() {
        log.info("M=validateAudioLimit, I=Iniciando validação de limite de áudio.")
        val user = userService.getUserByContext()
        val plan = getPlanTypeById(user.userPlan!!.plan.id)

        plan?.maxAudioBillEntries?.let {
            if (it > 1000) {
                log.info("M=validateAudioLimit, I=Usuário com plano de entradas de áudio ilimitadas.")
                return
            }
        }

        val currentMonthEntries = billService.countAudioEntriesCurrentMonth(user.id!!)
        log.info("M=validateAudioLimit, I=Entradas de áudio no mês atual: $currentMonthEntries.")

        if (currentMonthEntries >= plan!!.maxAudioBillEntries) {
            log.warn("M=validateAudioLimit, W=Limite do plano atingido para o usuário ${user.id}.")
            throw RuntimeException("Limite do plano FREE atingido: 2 contas via áudio por mês.")
        }
        log.info("M=validateAudioLimit, I=Validação de limite de áudio bem-sucedida.")
    }

    private fun getBillFromWhisperPrompt(text: String): String {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

        return """
            Você é um assistente financeiro. Hoje é $today.
            Analise o texto do usuário e extraia os dados para uma conta.
            
            REGRAS:
            1. Se o usuário quiser "conversar", "falar com a Savi", "tirar dúvida", "abrir chat" ou algo que não seja adicionar uma conta, retorne o JSON com "redirectAction": "CHAT_SAVI".
            2. Caso contrário, extraia os dados da conta.
            
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