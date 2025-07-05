package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "user_fs_coin")
data class UserFsCoin(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "user_id", unique = true, nullable = false)
    val userId: Long,

    @Column(name = "balance", nullable = false)
    var balance: Long = 0,

    @Embedded
    var audit: Audit? = null
)
