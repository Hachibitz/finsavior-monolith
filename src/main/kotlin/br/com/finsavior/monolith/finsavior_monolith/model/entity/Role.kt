package br.com.olxcarwatcher.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id

@Entity
data class Role(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val name: String,
    @Embedded
    val audit: Audit? = null
)