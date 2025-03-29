package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "plan")
data class Plan (
    @Id
    var id: String,

    var description: String,

    @Embedded
    var audit: Audit? = null
)