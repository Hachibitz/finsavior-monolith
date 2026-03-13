package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

@Entity
@Table(name = "goals")
class Goal(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false)
    var name: String,

    @Column(name = "target_amount", nullable = false)
    var targetAmount: BigDecimal,

    @Column(name = "current_amount", nullable = false)
    var currentAmount: BigDecimal,

    @Column(nullable = false)
    var deadline: LocalDate,

    var category: String?,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User
)
