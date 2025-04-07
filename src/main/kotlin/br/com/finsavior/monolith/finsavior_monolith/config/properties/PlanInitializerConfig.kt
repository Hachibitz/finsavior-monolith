package br.com.finsavior.monolith.finsavior_monolith.config.properties

import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

@Component
class PlanInitializerConfig(
    private val planIdProperties: PlanIdProperties
) {
    @PostConstruct
    fun init() {
        PlanTypeEnum.initialize(planIdProperties)
    }
}