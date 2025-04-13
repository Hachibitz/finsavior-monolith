package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.model.dto.FinancialSummary
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CurrentFinancialSituationEnum
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class FinancialService(
    private val billService: BillService
) {

    fun getUserFinancialSummary(userId: Long, targetDate: String? = null): FinancialSummary {
        val formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val formattedDate = if (targetDate != null) {
            billService.formatBillDate(targetDate)
        } else {
            val now = LocalDateTime.now()
            billService.formatBillDate(now.format(formatter))
        }

        val mainTableData = billService.loadMainTableData(formattedDate)
        val cardTableData = billService.loadCardTableData(formattedDate)
        val assetsTableData = billService.loadAssetsTableData(formattedDate)
        val paymentCardTableData = billService.loadPaymentCardTableData(formattedDate)

        val cardPaymentTotal = paymentCardTableData.sumOf { it.billValue }
        val totalPaid = mainTableData.filter { it.paid }.sumOf { it.billValue } + cardPaymentTotal
        val currentlyAvailableIncome = assetsTableData
            .filter { it.billType == "Caixa" || it.billType == "Ativo" }
            .sumOf { it.billValue }
        val totalDebit =
            mainTableData.filter { it.billType == "Passivo" }.sumOf { it.billValue } +
                    cardTableData.sumOf { it.billValue }
        val totalLeft = totalDebit - totalPaid
        val foreseenBalance = currentlyAvailableIncome - totalDebit
        val totalCreditCardExpense = cardTableData.sumOf { it.billValue }
        val totalUnpaidExpenses = totalLeft

        val categoryExpenses: Map<String, Double> = mainTableData
            .groupBy { it.billType }
            .mapValues { (_, rows) -> rows.sumOf { it.billValue }.toDouble() }

        val situation = when {
            foreseenBalance < BigDecimal.ZERO -> CurrentFinancialSituationEnum.VERMELHO
            (foreseenBalance / currentlyAvailableIncome * BigDecimal(100)) >= BigDecimal(10) -> CurrentFinancialSituationEnum.AZUL
            else -> CurrentFinancialSituationEnum.AMARELO
        }

        return FinancialSummary(
            currentSituation = situation,
            foreseenBalance = foreseenBalance,
            totalBalance = currentlyAvailableIncome,
            totalExpenses = totalDebit,
            totalUnpaidExpenses = totalUnpaidExpenses,
            totalCreditCardExpense = totalCreditCardExpense,
            totalPaidCreditCard = cardPaymentTotal,
            categoryExpenses = categoryExpenses
        )
    }
}