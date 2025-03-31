package br.com.finsavior.monolith.finsavior_monolith.repository

import br.com.finsavior.monolith.finsavior_monolith.model.entity.UserPlan
import org.springframework.data.jpa.repository.JpaRepository

interface UserPlanRepository : JpaRepository<UserPlan, Long>