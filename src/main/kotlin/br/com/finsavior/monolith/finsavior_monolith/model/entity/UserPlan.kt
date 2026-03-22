package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.SubscriptionStatusEnum
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

@Entity
@Table(name = "user_plan")
class UserPlan (
    @Id
    @Column(name = "user_id")
    var userId: Long,

    @ManyToOne
    @JoinColumn(name = "plan_id")
    var plan: Plan,

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status")
    var subscriptionStatus: SubscriptionStatusEnum? = SubscriptionStatusEnum.ACTIVE,

    @Embedded
    var audit: Audit? = null
)
