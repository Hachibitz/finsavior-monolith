package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
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

    @Embedded
    var audit: Audit? = null
)