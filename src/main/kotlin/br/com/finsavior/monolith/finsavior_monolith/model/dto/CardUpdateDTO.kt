package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.CardStyleEnum
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CardUpdateDTO(
    @field:NotNull
    val id: Long,

    @field:NotBlank
    val name: String,

    val style: CardStyleEnum?
)
