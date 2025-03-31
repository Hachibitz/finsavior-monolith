package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table

@Entity
@Table(name = "user")
data class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
    @Column(name="username", unique = true)
    val username: String,
    var password: String,

    @Column(name = "first_name")
    var firstName: String,

    @Column(name = "last_name")
    var lastName: String,

    @Column(name="email", unique = true)
    var email: String,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinColumn(name = "id", referencedColumnName = "user_id")
    var userPlan: UserPlan?,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    var userProfile: UserProfile?,

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf(),
    var enabled: Boolean,

    @Embedded
    var audit: Audit? = null
) {
    fun getFirstAndLastName(): String {
        val completeName = (this.firstName + this.lastName).trim { it <= ' ' }

        val nameParts: Array<String?> =
            completeName.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        if (nameParts.size < 2) {
            return completeName
        }

        val singleLastName = nameParts[nameParts.size - 1]

        return this.firstName + " " + singleLastName
    }

    constructor() : this(
        null,
        "",
        "",
        "",
        "",
        "",
        null,
        null,
        mutableSetOf(),
        true,
        null
    )
}