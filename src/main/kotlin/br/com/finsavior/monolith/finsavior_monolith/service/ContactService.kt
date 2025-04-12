package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.CommunicationException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ContactTicket
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class ContactService(
    private val emailService: EmailService
) {

    fun receiveTicket(ticket: ContactTicket): ResponseEntity<Void> {
        validateTicket(ticket)
        return processTicket(ticket)
    }

    private fun validateTicket(ticket: ContactTicket) {
        require(ticket.isAuthenticated || ticket.email == ticket.emailConfirmation) {
            "Emails não coincidem"
        }
    }

    private fun processTicket(ticket: ContactTicket): ResponseEntity<Void> {
        return try {
            emailService.sendUserContactToApp(ticket)
            emailService.sendConfirmationToUser(ticket)
            ResponseEntity.ok().build()
        } catch (ex: Exception) {
            throw CommunicationException("Falha ao enviar ticket ou confirmação", ex)
        }
    }
}