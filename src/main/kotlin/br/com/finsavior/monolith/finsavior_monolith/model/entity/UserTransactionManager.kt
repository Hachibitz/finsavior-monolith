package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Embedded

@Entity
@Table(name = "user_transaction_manager")
data class UserTransactionManager(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
    private var id: Int? = null,

    @Column(name = "user_id")
    var userId: Long? = null,

    @Column(name = "status_id")
    var statusId: Int? = null,

    @Embedded
    var audit: Audit? = null
)