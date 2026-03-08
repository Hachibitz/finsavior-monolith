package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.CategoryNotFoundException
import br.com.finsavior.monolith.finsavior_monolith.exception.UnauthorizedException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CategoryDTO
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toCategory
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.CategoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CategoryService(
    private val categoryRepository: CategoryRepository,
    private val userService: UserService
) {

    @Transactional
    fun createCategory(categoryDTO: CategoryDTO): CategoryDTO {
        val user = userService.getUserByContext()
        val category = categoryDTO.toCategory(user)
        val savedCategory = categoryRepository.save(category)
        return savedCategory.toDTO()
    }

    @Transactional(readOnly = true)
    fun listCategories(): List<CategoryDTO> {
        val user = userService.getUserByContext()
        val defaultCategories = categoryRepository.findByUserIdIsNull()
        val userCategories = categoryRepository.findByUserId(user.id!!)
        return (defaultCategories + userCategories).map { it.toDTO() }
    }

    @Transactional
    fun updateCategory(id: String, categoryDTO: CategoryDTO): CategoryDTO {
        val user = userService.getUserByContext()
        val category = categoryRepository.findById(id)
            .orElseThrow { CategoryNotFoundException("Categoria com id $id não encontrada") }

        if (category.user?.id != user.id) {
            throw UnauthorizedException("A categoria não pertence ao usuário atual")
        }

        category.name = categoryDTO.name
        category.icon = categoryDTO.icon
        category.color = categoryDTO.color
        category.type = categoryDTO.type ?: category.type

        val updatedCategory = categoryRepository.save(category)
        return updatedCategory.toDTO()
    }

    @Transactional
    fun deleteCategory(id: String) {
        val user = userService.getUserByContext()
        val category = categoryRepository.findById(id)
            .orElseThrow { CategoryNotFoundException("Categoria com id $id não encontrada") }

        if (category.user?.id != user.id) {
            throw UnauthorizedException("A categoria não pertence ao usuário atual")
        }

        categoryRepository.delete(category)
    }
}
