package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.GoalDTO
import br.com.finsavior.monolith.finsavior_monolith.service.GoalService
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
@RequestMapping("goals")
class GoalController(
    private val goalService: GoalService
) {

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    fun getGoals(): List<GoalDTO> =
        goalService.getGoals()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun addGoal(@Valid @RequestBody goalDTO: GoalDTO): GoalDTO =
        goalService.addGoal(goalDTO)

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    fun updateGoal(@PathVariable id: String, @Valid @RequestBody goalDTO: GoalDTO): GoalDTO =
        goalService.updateGoal(id, goalDTO)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGoal(@PathVariable id: String) =
        goalService.deleteGoal(id)
}
