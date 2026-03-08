package br.com.finsavior.monolith.finsavior_monolith.model.mapper

import br.com.finsavior.monolith.finsavior_monolith.model.dto.CategoryDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Category
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CategoryType

fun Category.toDTO() = CategoryDTO(
    id = this.id,
    name = this.name,
    icon = this.icon,
    color = this.color,
    type = this.type
)

fun CategoryDTO.toCategory(user: User? = null) = Category(
    name = this.name,
    icon = this.icon,
    color = this.color,
    type = this.type ?: CategoryType.expense,
    user = user
)
