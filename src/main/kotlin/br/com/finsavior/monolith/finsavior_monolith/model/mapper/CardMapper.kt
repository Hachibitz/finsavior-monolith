package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Card

fun Card.toDTO(): CardDTO = CardDTO(
    id = this.id,
    name = this.name,
    style = this.style
)

