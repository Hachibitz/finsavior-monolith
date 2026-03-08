package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.CategoryType

data class CategoryDTO(
    val id: String?,
    val name: String,
    val icon: String,
    val color: String,
    val type: CategoryType?
)
