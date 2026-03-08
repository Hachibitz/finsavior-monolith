package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.Category
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<Category, String> {
    fun findByUserId(userId: Long): List<Category>
    fun findByUserIdIsNull(): List<Category>
}
