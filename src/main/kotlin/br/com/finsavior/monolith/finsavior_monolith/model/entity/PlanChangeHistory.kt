package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "plan_history")
data class PlanChangeHistory(

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name = "user_id")
    var userId: Long,
    @Column(name = "external_user_id")
    var externalUserId: String? = null,
    @Column(name = "plan_id")
    var planId: String,
    @Column(name = "plan_type")
    var planType: PlanTypeEnum,
    @Column(name = "update_time")
    var updateTime: LocalDateTime,

    @Embedded
    val audit: Audit? = null
)
