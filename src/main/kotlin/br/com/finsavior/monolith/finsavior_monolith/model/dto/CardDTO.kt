package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.CardStyleEnum

data class CardDTO(
    val id: Long?,
    val name: String,
    val style: CardStyleEnum?
)
