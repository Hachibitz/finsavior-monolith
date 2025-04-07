package br.com.finsavior.monolith.finsavior_monolith.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "finsavior.plans")
class PlanIdProperties {
    lateinit var STRIPE_BASIC_MONTHLY: String
    lateinit var STRIPE_BASIC_ANNUAL: String
    lateinit var STRIPE_PLUS_MONTHLY: String
    lateinit var STRIPE_PLUS_ANNUAL: String
    lateinit var STRIPE_PREMIUM_MONTHLY: String
    lateinit var STRIPE_PREMIUM_ANNUAL: String
}
