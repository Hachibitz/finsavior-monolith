package br.com.finsavior.monolith.finsavior_monolith.model.dto

data class ProfileDataDTO (
    val username: String,
    val profilePicture: ByteArray? = null,
    val email: String,
    val plan: PlanDTO
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProfileDataDTO

        if (username != other.username) return false
        if (!profilePicture.contentEquals(other.profilePicture)) return false
        if (email != other.email) return false
        if (plan != other.plan) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + (profilePicture?.contentHashCode() ?: 0)
        result = 31 * result + email.hashCode()
        result = 31 * result + plan.hashCode()
        return result
    }
}
