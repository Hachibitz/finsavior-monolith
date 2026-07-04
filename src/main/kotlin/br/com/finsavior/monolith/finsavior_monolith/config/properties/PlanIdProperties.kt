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
    var PLAY_BASIC_MONTHLY: String = "finsavior_basic_monthly"
    var PLAY_BASIC_ANNUAL: String = "finsavior_basic_annual"
    var PLAY_PLUS_MONTHLY: String = "finsavior_plus_monthly"
    var PLAY_PLUS_ANNUAL: String = "finsavior_plus_annual"
    var PLAY_PREMIUM_MONTHLY: String = "finsavior_premium_monthly"
    var PLAY_PREMIUM_ANNUAL: String = "finsavior_premium_annual"
    var PLAY_BASIC_MONTHLY_BASE_PLAN: String = "monthly"
    var PLAY_BASIC_ANNUAL_BASE_PLAN: String = "annual"
    var PLAY_PLUS_MONTHLY_BASE_PLAN: String = "monthly"
    var PLAY_PLUS_ANNUAL_BASE_PLAN: String = "annual"
    var PLAY_PREMIUM_MONTHLY_BASE_PLAN: String = "monthly"
    var PLAY_PREMIUM_ANNUAL_BASE_PLAN: String = "annual"
}
