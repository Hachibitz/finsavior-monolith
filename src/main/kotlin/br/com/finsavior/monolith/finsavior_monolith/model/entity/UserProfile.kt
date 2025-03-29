package br.com.finsavior.monolith.finsavior_monolith.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table

@Entity
@Table(name = "user_profile")
data class UserProfile (
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long,

    @Column(name = "name")
    var name: String,

    @Column(name = "email")
    var email: String,

    @Column(name = "profile_picture")
    @Lob
    var profilePicture: ByteArray? = null,

    @Embedded
    var audit: Audit? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserProfile

        if (id != other.id) return false
        if (name != other.name) return false
        if (email != other.email) return false
        if (!profilePicture.contentEquals(other.profilePicture)) return false
        if (audit != other.audit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + (profilePicture?.contentHashCode() ?: 0)
        result = 31 * result + audit.hashCode()
        return result
    }
}