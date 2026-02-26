package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.CommunicationException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ContactTicket
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class ContactService(
    private val emailService: EmailService
) {

    fun receiveTicket(ticket: ContactTicket) {
        val authenticated = isRequestAuthenticated()
        validateTicket(ticket, authenticated)
        processTicket(ticket)
    }

    private fun isRequestAuthenticated(): Boolean {
        val auth = SecurityContextHolder.getContext().authentication
        return auth != null && auth.isAuthenticated && auth.principal != "anonymousUser"
    }

    private fun validateTicket(ticket: ContactTicket, authenticated: Boolean) {
        if (!authenticated) {
            require(ticket.email == ticket.emailConfirmation) { "Emails não coincidem" }
        }
    }

    private fun processTicket(ticket: ContactTicket) {
        try {
            emailService.sendUserContactToApp(ticket)
            emailService.sendConfirmationToUser(ticket)
        } catch (ex: Exception) {
            throw CommunicationException("Falha ao enviar ticket ou confirmação", ex)
        }
    }
}