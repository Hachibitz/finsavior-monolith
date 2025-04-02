package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.ExternalUserDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.ExternalUser

fun ExternalUser.toExternalUserDTO(): ExternalUserDTO =
    ExternalUserDTO(
        userId = this.userId,
        email = this.externalUserEmail,
        subscriptionId = this.subscriptionId,
        externalUserId = this.externalUserId
    )