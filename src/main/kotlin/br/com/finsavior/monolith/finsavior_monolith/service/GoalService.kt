package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.GoalNotFoundException
import br.com.finsavior.monolith.finsavior_monolith.exception.UnauthorizedException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.GoalDTO
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toDTO
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toEntity
import br.com.finsavior.monolith.finsavior_monolith.repository.GoalRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoalService(
    private val goalRepository: GoalRepository,
    private val userService: UserService
) {

    @Transactional(readOnly = true)
    fun getGoals(): List<GoalDTO> {
        val user = userService.getUserByContext()
        return goalRepository.findByUserId(user.id!!).map { it.toDTO() }
    }

    @Transactional
    fun addGoal(goalDTO: GoalDTO): GoalDTO {
        val user = userService.getUserByContext()
        val goal = goalDTO.toEntity(user)
        val savedGoal = goalRepository.save(goal)
        return savedGoal.toDTO()
    }

    @Transactional
    fun updateGoal(id: String, goalDTO: GoalDTO): GoalDTO {
        val user = userService.getUserByContext()
        val goal = goalRepository.findById(id)
            .orElseThrow { GoalNotFoundException("Meta com id $id não encontrada") }

        if (goal.user.id != user.id) {
            throw UnauthorizedException("A meta não pertence ao usuário atual")
        }

        goal.name = goalDTO.name
        goal.targetAmount = goalDTO.targetAmount
        goal.currentAmount = goalDTO.currentAmount
        goal.deadline = java.time.LocalDate.parse(goalDTO.deadline)
        goal.category = goalDTO.category

        val updatedGoal = goalRepository.save(goal)
        return updatedGoal.toDTO()
    }

    @Transactional
    fun deleteGoal(id: String) {
        val user = userService.getUserByContext()
        val goal = goalRepository.findById(id)
            .orElseThrow { GoalNotFoundException("Meta com id $id não encontrada") }

        if (goal.user.id != user.id) {
            throw UnauthorizedException("A meta não pertence ao usuário atual")
        }

        goalRepository.delete(goal)
    }
}
