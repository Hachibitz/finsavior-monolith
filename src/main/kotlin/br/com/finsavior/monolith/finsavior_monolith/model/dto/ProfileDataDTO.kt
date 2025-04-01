package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class ProfileDataDTO (
    val username: String,
    val profilePicture: ByteArray? = null,
    val email: String,
    val plan: PlanDTO,
    val firstName: String,
    val lastName: String,
    val name: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProfileDataDTO

        if (username != other.username) return false
        if (!profilePicture.contentEquals(other.profilePicture)) return false
        if (email != other.email) return false
        if (plan != other.plan) return false
        if (firstName != other.firstName) return false
        if (lastName != other.lastName) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + (profilePicture?.contentHashCode() ?: 0)
        result = 31 * result + email.hashCode()
        result = 31 * result + plan.hashCode()
        result = 31 * result + firstName.hashCode()
        result = 31 * result + lastName.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }
}
