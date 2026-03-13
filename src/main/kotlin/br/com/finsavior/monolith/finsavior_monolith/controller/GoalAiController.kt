package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.GoalAdviceDTO
import br.com.finsavior.monolith.finsavior_monolith.service.GoalAiService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ai/goals")
class GoalAiController(
    private val goalAiService: GoalAiService
) {

    @GetMapping("/{goalId}/advice")
    @ResponseStatus(HttpStatus.OK)
    fun getGoalAdvice(
        @PathVariable goalId: String,
        @RequestParam(defaultValue = "false") useCoins: Boolean
    ): Map<String, String> {
        return goalAiService.getGoalAdvice(goalId, useCoins)
    }

    @GetMapping("/{goalId}/history")
    @ResponseStatus(HttpStatus.OK)
    fun getAdvicesForGoal(@PathVariable goalId: String): List<GoalAdviceDTO> {
        return goalAiService.getAdvicesForGoal(goalId)
    }
}
