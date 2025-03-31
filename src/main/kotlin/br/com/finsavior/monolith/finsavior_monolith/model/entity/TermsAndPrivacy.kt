package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.TermsAndPrivacyEnum
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "terms_and_privacy")
data class TermsAndPrivacy(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    val type: TermsAndPrivacyEnum,
    val content: String,
    val version: String,
    @Column(name = "validity_start_date")
    val validityStartDate: LocalDateTime,

    @Embedded
    val audit: Audit? = null
)