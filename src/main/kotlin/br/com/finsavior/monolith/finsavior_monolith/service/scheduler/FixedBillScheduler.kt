package br.com.finsavior.monolith.finsavior_monolith.service.scheduler

import org.springframework.stereotype.Component

/**
 * Kept as a bean placeholder for backwards compatibility; fixed bills are now
 * projected on read from `fixed_bill` without materializing monthly rows.
 */
@Component
class FixedBillScheduler
