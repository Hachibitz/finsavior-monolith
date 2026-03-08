package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.finsavior.monolith.finsavior_monolith.model.enums.CategoryType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.*

@Entity
@Table(name = "categories")
class Category(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, length = 100)
    var name: String,

    @Column(nullable = false, length = 50)
    var icon: String,

    @Column(nullable = false, length = 20)
    var color: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: CategoryType = CategoryType.expense,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: User? = null
)
