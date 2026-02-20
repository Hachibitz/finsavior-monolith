package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.AiBillExtractionDTO
import br.com.finsavior.monolith.finsavior_monolith.model.enums.DocumentTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.service.AiDocumentService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/ai/document")
class AiDocumentController(
    private val aiDocumentService: AiDocumentService
) {

    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.OK)
    fun uploadDocument(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("docType") docType: DocumentTypeEnum,
        @RequestParam(value = "password", required = false) password: String?,
        @RequestParam(value = "isUsingCoins", required = false, defaultValue = "false") isUsingCoins: Boolean
    ): ResponseEntity<List<AiBillExtractionDTO>> {
        val bills = when (isUsingCoins) {
            true -> aiDocumentService.processDocumentWithCoins(file, docType.name, password)
            false -> aiDocumentService.processDocument(file, docType.name, password)
        }
        return ResponseEntity.ok(bills)
    }
}