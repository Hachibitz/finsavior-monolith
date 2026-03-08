package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.CategoryDTO
import br.com.finsavior.monolith.finsavior_monolith.service.CategoryService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("categories")
class CategoryController(
    private val categoryService: CategoryService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(@Valid @RequestBody categoryDTO: CategoryDTO): CategoryDTO =
        categoryService.createCategory(categoryDTO)

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun listCategories(): List<CategoryDTO> =
        categoryService.listCategories()

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun updateCategory(@PathVariable id: String, @Valid @RequestBody categoryDTO: CategoryDTO): CategoryDTO =
        categoryService.updateCategory(id, categoryDTO)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(@PathVariable id: String) =
        categoryService.deleteCategory(id)
}
