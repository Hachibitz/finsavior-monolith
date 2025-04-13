package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.ProfileDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.SignUpDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User

fun SignUpDTO.toUser(): User =
    User(
        email = this.email,
        username = this.username,
        password = "",
        firstName = this.firstName,
        lastName = this.lastName,
        userPlan = null,
        userProfile = null,
        enabled = true,
        audit = Audit(),
    )

fun User.toUserProfileDTO(): ProfileDataDTO =
    ProfileDataDTO(
        id = this.id,
        username = this.username,
        profilePicture = this.userProfile!!.profilePicture,
        email = this.userProfile!!.email,
        plan = this.userPlan!!.plan.toPlanDTO(),
        firstName = this.firstName,
        lastName = this.lastName,
        name = this.getFirstAndLastName(),
    )