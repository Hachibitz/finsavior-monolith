package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.service.TermsAndPrivacyService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/terms-and-privacy")
class TermsAndPrivacyController(
    private val termsAndPrivacyService: TermsAndPrivacyService
) {

    @GetMapping("/get-terms")
    @ResponseStatus(HttpStatus.OK)
    fun getTerms(): String =
        termsAndPrivacyService.getTerms()

    @GetMapping("/get-privacy-policy")
    @ResponseStatus(HttpStatus.OK)
    fun getPrivacyPolicy(): String =
        termsAndPrivacyService.getPrivacyPolicy()
}