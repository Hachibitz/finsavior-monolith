package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.ExternalProvider
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "external_user")
data class ExternalUser(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(name = "subscription_id")
    var subscriptionId: String,

    @Column(name = "external_user_id")
    var externalUserId: String,

    @Column(name = "external_user_email")
    var externalUserEmail: String,

    @Column(name = "service_name")
    var externalProvider: ExternalProvider,

    @Column(name = "user_id")
    var userId: Long,

    @Embedded
    var audit: Audit? = null
)
