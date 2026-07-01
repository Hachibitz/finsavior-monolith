package br.com.finsavior.monolith.finsavior_monolith.config.properties

import br.com.finsavior.monolith.finsavior_monolith.model.entity.Audit
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Plan
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.PlanRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class PlanInitializerConfig(
    private val planIdProperties: PlanIdProperties,
    private val planRepository: PlanRepository
) {
    @PostConstruct
    fun init() {
        PlanTypeEnum.initialize(planIdProperties)
        PlanTypeEnum.entries
            .filter { it != PlanTypeEnum.FREE && it.id != "UNSET" }
            .forEach { planType ->
                if (!planRepository.existsById(planType.id)) {
                    planRepository.save(
                        Plan(
                            id = planType.id,
                            description = planType,
                            hasUserFreeAnalysis = false,
                            audit = Audit()
                        )
                    )
                }
            }
    }
}