package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.ContactTicket
import br.com.finsavior.monolith.finsavior_monolith.service.ContactService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/contact")
class ContactController(
    private val contactService: ContactService
) {

    @PostMapping
    fun receiveTicket(@RequestBody ticket: ContactTicket): ResponseEntity<Void> =
        contactService.receiveTicket(ticket)
}