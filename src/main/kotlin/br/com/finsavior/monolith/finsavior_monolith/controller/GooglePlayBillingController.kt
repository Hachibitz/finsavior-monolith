package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.config.properties.GooglePlayProperties
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayProductCatalogResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayRtdnMessage
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayVerifySubscriptionRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GooglePlayVerifySubscriptionResponse
import br.com.finsavior.monolith.finsavior_monolith.service.GooglePlayBillingService
import com.google.auth.oauth2.TokenVerifier
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

@RestController
@RequestMapping("payment/google-play")
class GooglePlayBillingController(
    private val googlePlayBillingService: GooglePlayBillingService,
    private val properties: GooglePlayProperties
) {

    private val log = KotlinLogging.logger {}

    @GetMapping("/products")
    fun getProducts(): GooglePlayProductCatalogResponse =
        googlePlayBillingService.getProductCatalog()

    @PostMapping("/verify-subscription")
    @ResponseStatus(HttpStatus.OK)
    fun verifySubscription(
        @Valid @RequestBody request: GooglePlayVerifySubscriptionRequest
    ): GooglePlayVerifySubscriptionResponse =
        googlePlayBillingService.verifyAndActivateSubscription(request)

    /**
     * Pub/Sub push endpoint for Real-Time Developer Notifications. Always
     * returns 200 so Pub/Sub doesn't redeliver forever; unauthenticated or
     * malformed requests are simply ignored.
     */
    @PostMapping("/rtdn")
    @ResponseStatus(HttpStatus.OK)
    fun handleRealTimeDeveloperNotification(
        @RequestBody body: GooglePlayRtdnMessage,
        @RequestParam(required = false) token: String?,
        @RequestHeader(value = "Authorization", required = false) authorization: String?
    ) {
        if (!isAuthorizedRtdn(token, authorization)) {
            log.warn { "RTDN rejected: failed authentication" }
            return
        }
        val data = body.message?.data ?: return
        googlePlayBillingService.handleRtdnNotification(data)
    }

    /**
     * Prefers Pub/Sub OIDC (Google-signed JWT in the Authorization header);
     * falls back to the shared query token compared in constant time. If
     * neither mechanism is configured the endpoint accepts all messages —
     * verification of the purchase against the Google API still applies.
     */
    private fun isAuthorizedRtdn(queryToken: String?, authorization: String?): Boolean {
        if (properties.rtdnOidcAudience.isNotBlank()) {
            return verifyOidcToken(authorization)
        }
        val expected = properties.rtdnVerificationToken
        if (expected.isBlank()) return true
        if (queryToken == null) return false
        return MessageDigest.isEqual(expected.toByteArray(), queryToken.toByteArray())
    }

    private fun verifyOidcToken(authorization: String?): Boolean {
        val bearer = authorization?.removePrefix("Bearer ")?.trim()
        if (bearer.isNullOrBlank() || bearer == authorization) return false
        return try {
            val verifier = TokenVerifier.newBuilder()
                .setAudience(properties.rtdnOidcAudience)
                .setIssuer("https://accounts.google.com")
                .build()
            val jws = verifier.verify(bearer)
            val email = jws.payload["email"] as? String
            val expectedEmail = properties.rtdnServiceAccountEmail
            expectedEmail.isBlank() || expectedEmail.equals(email, ignoreCase = true)
        } catch (e: Exception) {
            log.warn { "RTDN OIDC verification failed: ${e.message}" }
            false
        }
    }
}
