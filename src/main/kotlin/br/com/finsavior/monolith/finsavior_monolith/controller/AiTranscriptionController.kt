package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiBillExtractionDTO
import br.com.finsavior.monolith.finsavior_monolith.service.AiTranscriptionService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/ai/transcription")
class AiTranscriptionController(
    private val aiTranscriptionService: AiTranscriptionService
) {

    @PostMapping("/process-audio", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun processAudio(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "isUsingCoins", required = false, defaultValue = "false") isUsingCoins: Boolean,
    ): ResponseEntity<AiBillExtractionDTO> {
        val billData = aiTranscriptionService.processAudioToBill(file, isUsingCoins)
        return ResponseEntity.ok(billData)
    }

    @PostMapping("/transcribe")
    fun transcribeAudio(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(value = "isUsingCoins", required = false, defaultValue = "false") isUsingCoins: Boolean,
    ): ResponseEntity<Map<String, String>> {
        val text = aiTranscriptionService.transcribeOnly(file, isUsingCoins)
        return ResponseEntity.ok(mapOf("text" to text))
    }
}