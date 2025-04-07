package br.com.finsavior.monolith.finsavior_monolith.integration.client.config

import feign.RequestInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
class StripeClientConfig {

    @Value("\${stripe.secret-key}")
    private lateinit var stripeSecretKey: String

    @Bean
    fun stripeAuthInterceptor(): RequestInterceptor {
        val encodedKey = Base64.getEncoder().encodeToString("$stripeSecretKey:".toByteArray())

        return RequestInterceptor { template ->
            template.header("Authorization", "Basic $encodedKey")
        }
    }
}
