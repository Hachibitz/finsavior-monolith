package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.ProfileDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User

fun User.toUserDTO(): ProfileDataDTO {
    return ProfileDataDTO(
        username = this.username,
        profilePicture = this.userProfile.profilePicture,
        email = this.userProfile.email,
        plan = this.userPlan.plan.toPlanDTO(),
    )
}