package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal

/**
 * Display-only prices for the Plans screen, keyed by tier + billing + currency.
 * Does NOT control Stripe/Play Store billing amounts — those live in each store.
 */
@Entity
@Table(
    name = "plan_display_price",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_plan_display_price",
            columnNames = ["plan_tier", "billing_cycle", "currency"]
        )
    ]
)
data class PlanDisplayPrice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    /** FREE | BASIC | PLUS | PREMIUM */
    @Column(name = "plan_tier", nullable = false, length = 16)
    var planTier: String,

    /** MONTHLY | ANNUAL */
    @Column(name = "billing_cycle", nullable = false, length = 16)
    var billingCycle: String,

    /** ISO 4217, e.g. BRL, USD, EUR */
    @Column(name = "currency", nullable = false, length = 8)
    var currency: String,

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    var amount: BigDecimal,

    @Embedded
    var audit: Audit? = null
)
