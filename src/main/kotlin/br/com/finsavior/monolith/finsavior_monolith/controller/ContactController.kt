package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.ContactTicket
import br.com.finsavior.monolith.finsavior_monolith.service.ContactService
import br.com.finsavior.monolith.finsavior_monolith.service.RateLimitService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/contact")
class ContactController(
    private val contactService: ContactService,
    private val rateLimitService: RateLimitService
) {

    /**
     * Public endpoint for unauthenticated users. Rate-limited by IP (3 per hour).
     * Requires email confirmation to match (validated by ContactService).
     */
    @PostMapping("/public")
    fun receivePublicTicket(
        request: HttpServletRequest,
        @Valid @RequestBody ticket: ContactTicket
    ): ResponseEntity<Void> {
        val clientIp = extractClientIp(request)
        return if (rateLimitService.tryConsume(clientIp)) {
            contactService.receiveTicket(ticket)
            ResponseEntity.status(HttpStatus.CREATED).build()
        } else {
            ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }
    }

    /**
     * Authenticated endpoint. Token should be sent via Authorization header.
     * No rate limit and email confirmation is not required.
     */
    @PostMapping
    fun receiveAuthenticatedTicket(@Valid @RequestBody ticket: ContactTicket): ResponseEntity<Void> {
        contactService.receiveTicket(ticket)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    private fun extractClientIp(request: HttpServletRequest): String {
        val header = request.getHeader("X-Forwarded-For")
        if (!header.isNullOrBlank()) return header.split(",")[0].trim()
        return request.remoteAddr ?: "unknown"
    }
}