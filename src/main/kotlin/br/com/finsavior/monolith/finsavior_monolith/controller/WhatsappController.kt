package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.service.TwilioService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("whatsapp")
class WhatsappController(
    private val twilioService: TwilioService
) {

    @GetMapping("/agent-number")
    @ResponseStatus(HttpStatus.OK)
    fun getAgentNumber(): Map<String, String> =
        mapOf("phoneNumber" to twilioService.getAgentNumber())
}
