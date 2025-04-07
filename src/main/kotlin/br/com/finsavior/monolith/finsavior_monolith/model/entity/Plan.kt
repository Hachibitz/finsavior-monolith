package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "plan")
data class Plan (
    @Id
    var id: String,

    @Enumerated(EnumType.STRING)
    var description: PlanTypeEnum,

    var hasUserFreeAnalysis: Boolean = false,

    @Embedded
    var audit: Audit? = null
)