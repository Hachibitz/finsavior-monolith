package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.exception.CommunicationException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ContactTicket
import br.com.finsavior.monolith.finsavior_monolith.service.EmailService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/contact")
class ContactController(
    private val emailService: EmailService
) {

    @PostMapping
    fun receiveTicket(@RequestBody ticket: ContactTicket): ResponseEntity<Void> {
        try {
            //alterar para fila rabbit
            emailService.sendUserContactToApp(ticket)
            emailService.sendConfirmationToUser(ticket)
            return ResponseEntity.ok().build()
        } catch (e: Exception) {
            throw CommunicationException("Falha ao enviar ticket ou confirmação")
        }
    }
}