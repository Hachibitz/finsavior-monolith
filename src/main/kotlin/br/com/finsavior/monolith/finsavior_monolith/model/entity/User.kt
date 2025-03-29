package br.com.finsavior.monolith.finsavior_monolith.model.entity

import br.com.olxcarwatcher.model.entity.Role
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToOne

@Entity
data class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val username: String,
    var password: String,

    @Column(name = "first_name")
    var firstName: String? = null,

    @Column(name = "last_name")
    var lastName: String? = null,

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL]) @JoinColumn(
        name = "id",
        referencedColumnName = "user_id"
    )
    val userPlan: UserPlan,

    @OneToOne(mappedBy = "user", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val userProfile: UserProfile,

    var name: String,
    var phone: String,
    @ManyToMany(fetch = FetchType.EAGER)
    val roles: Set<Role> = setOf(),
    val enabled: Boolean,

    @Embedded
    val audit: Audit? = null
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
}