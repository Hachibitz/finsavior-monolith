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
     * Runs daily at 03:00. The underlying generation is idempotent, so a daily
     * cadence safely covers month rollovers and any missed runs after restarts.
     */
    @Scheduled(cron = "0 0 3 * * *")
    fun generateFixedBills() {
        log.info("Iniciando geração agendada de contas fixas")
        runCatching { fixedBillService.generateUpcomingInstancesForAllActive() }
            .onFailure { log.error("Falha na geração agendada de contas fixas: ${it.message}", it) }
        log.info("Geração agendada de contas fixas finalizada")
    }
}
