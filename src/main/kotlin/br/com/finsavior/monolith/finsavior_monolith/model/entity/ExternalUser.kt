package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.ExternalProvider
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "external_user")
data class ExternalUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "subscription_id")
    var subscriptionId: String? = null,

    @Column(name = "is_trial_used")
    var isTrialUsed: Boolean? = false,

    @Column(name = "external_user_id")
    var externalUserId: String? = null,

    @Column(name = "external_user_email")
    var externalUserEmail: String? = null,

    @Column(name = "service_name")
    @Enumerated(EnumType.STRING)
    var externalProvider: ExternalProvider,

    @Column(name = "user_id", unique = true)
    var userId: Long,

    @Embedded
    var audit: Audit? = null
)
