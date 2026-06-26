package br.com.finsavior.monolith.finsavior_monolith.service.scheduler

import br.com.finsavior.monolith.finsavior_monolith.service.FixedBillService
import mu.KLogger
import mu.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class FixedBillScheduler(
    private val fixedBillService: FixedBillService
) {

    private val log: KLogger = KotlinLogging.logger {}

    /**
     * Annual strategy: materializes all months from January through December.
     * Runs at 00:00 on January 1st and is idempotent.
     */
    @Scheduled(cron = "0 0 0 1 1 *")
    fun generateYearlyFixedBills() {
        log.info("Iniciando geração anual de contas fixas")
        runCatching { fixedBillService.generateYearlyInstancesForActiveBills() }
            .onFailure { log.error("Falha na geração anual de contas fixas: ${it.message}", it) }
        log.info("Geração anual de contas fixas finalizada")
    }

    /**
     * Monthly strategy: materializes only the current month. Runs at 00:00 on the
     * 1st day of every month and is idempotent.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    fun generateMonthlyFixedBills() {
        log.info("Iniciando geração mensal de contas fixas")
        runCatching { fixedBillService.generateCurrentMonthInstancesForActiveMonthlyBills() }
            .onFailure { log.error("Falha na geração mensal de contas fixas: ${it.message}", it) }
        log.info("Geração mensal de contas fixas finalizada")
    }
}
