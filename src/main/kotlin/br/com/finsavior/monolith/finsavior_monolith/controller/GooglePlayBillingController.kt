package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.config.properties.GooglePlayProperties
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayProductCatalogResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayRtdnMessage
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayVerifySubscriptionRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayVerifySubscriptionResponse
import br.com.finsavior.monolith.finsavior_monolith.service.GooglePlayBillingService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("payment/google-play")
class GooglePlayBillingController(
    private val googlePlayBillingService: GooglePlayBillingService,
    private val properties: GooglePlayProperties
) {

    @GetMapping("/products")
    fun getProducts(): GooglePlayProductCatalogResponse =
        googlePlayBillingService.getProductCatalog()

    @PostMapping("/verify-subscription")
    @ResponseStatus(HttpStatus.OK)
    fun verifySubscription(
        @Valid @RequestBody request: GooglePlayVerifySubscriptionRequest
    ): GooglePlayVerifySubscriptionResponse =
        googlePlayBillingService.verifyAndActivateSubscription(request)

    @PostMapping("/rtdn")
    @ResponseStatus(HttpStatus.OK)
    fun handleRealTimeDeveloperNotification(
        @RequestBody body: GooglePlayRtdnMessage,
        @RequestParam(required = false) token: String?
    ) {
        val expected = properties.rtdnVerificationToken
        if (expected.isNotBlank() && token != expected) {
            return
        }
        val data = body.message?.data ?: return
        googlePlayBillingService.handleRtdnNotification(data)
    }
}
