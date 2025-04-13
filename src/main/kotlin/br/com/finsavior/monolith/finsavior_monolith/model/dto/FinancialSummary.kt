package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.CurrentFinancialSituationEnum
import java.math.BigDecimal

data class FinancialSummary(
    val currentSituation: CurrentFinancialSituationEnum,
    val foreseenBalance: BigDecimal,
    val totalBalance: BigDecimal,
    val totalExpenses: BigDecimal,
    val totalUnpaidExpenses: BigDecimal,
    val totalCreditCardExpense: BigDecimal,
    val totalPaidCreditCard: BigDecimal,
    val categoryExpenses: Map<String, Double>
)