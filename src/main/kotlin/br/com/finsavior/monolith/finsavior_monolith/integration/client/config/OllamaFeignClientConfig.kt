package br.com.finsavior.monolith.finsavior_monolith.integration.client.config

import feign.Request
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OllamaFeignConfig {

    @Bean
    fun options(): Request.Options {
        return Request.Options(
            10_000,  // connectTimeout (ms)
            240_000   // readTimeout (ms)
        )
    }
}
