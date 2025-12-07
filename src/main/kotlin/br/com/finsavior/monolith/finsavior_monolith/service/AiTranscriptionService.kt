package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperApiException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiBillExtractionDTO
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
    @param:Value("\${ai.openai.api-key}") private val apiKey: String
) {
    companion object {
        const val OPEN_AI_TRANSCRIBE_AUDIO_API_URL = "https://api.openai.com/v1/audio/transcriptions"
        const val OPEN_AI_CHAT_COMPLETION_API_URL = "https://api.openai.com/v1/chat/completions"
    }

    private val restTemplate = RestTemplate()
    private val objectMapper = jacksonObjectMapper()

    fun processAudioToBill(audioFile: MultipartFile): AiBillExtractionDTO {
        val transcription = transcribeAudio(audioFile)
        return extractDataFromText(transcription)
    }

    private fun transcribeAudio(audioFile: MultipartFile): String {
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
            return response.body?.get("text") as String
        } catch (e: Exception) {
            throw WhisperApiException("Erro na transcrição: ${e.message}", e)
        }
    }

    private fun extractDataFromText(text: String): AiBillExtractionDTO {
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

            return objectMapper.readValue(content, AiBillExtractionDTO::class.java)
        } catch (e: Exception) {
            throw RuntimeException("Erro na interpretação: ${e.message}")
        }
    }

    private fun getBillFromWhisperPrompt(text: String): String {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

        return """
            Você é um assistente financeiro. Hoje é $today.
            Analise o texto do usuário e extraia os dados para uma conta.
            
            Retorne APENAS um JSON estrito (sem markdown) neste formato:
            {
                "billName": "string (curto, resumido)",
                "billValue": number,
                "billDescription": "string (o texto original)",
                "billCategory": "string (tente adivinhar: Alimentação, Transporte, Lazer, Saúde, Outras)",
                "isInstallment": boolean,
                "installmentCount": int (se não mencionado, null),
                "isRecurrent": boolean (se falar 'todo mês' ou 'fixo')
            }
            
            Texto: "$text"
        """.trimIndent()
    }
}