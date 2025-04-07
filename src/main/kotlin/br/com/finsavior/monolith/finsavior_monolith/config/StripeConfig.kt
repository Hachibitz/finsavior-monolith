package br.com.finsavior.monolith.finsavior_monolith.config

import com.stripe.Stripe
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

@Configuration
class StripeConfig(
    @Value("\${stripe.secret-key}") private val secretKey: String
) {
    @PostConstruct
    fun init() {
        Stripe.apiKey = secretKey
    }
}
