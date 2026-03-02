package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.CardStyleEnum
import jakarta.validation.constraints.NotBlank

data class CardRegisterDTO(
    @field:NotBlank
    val name: String,

    val style: CardStyleEnum?
)
