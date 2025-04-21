package br.com.finsavior.monolith.finsavior_monolith.util

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.enums.AnalysisTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.PlanTypeEnum
import java.util.*

/**
 * Utility class for common operations.
 */
class CommonUtils {

    companion object {
        fun getPlanTypeById(planTypeId: String): PlanTypeEnum? =
            Arrays.stream(PlanTypeEnum.entries.toTypedArray())
                .filter { planType -> planType.id == planTypeId }
                .findFirst()
                .orElse(null)

        fun getAnalysisTypeById(analysisTypeId: Int?): AnalysisTypeEnum? =
            Arrays.stream(AnalysisTypeEnum.entries.toTypedArray())
                .filter { analysis -> analysis.analysisTypeId == analysisTypeId }
                .findFirst()
                .orElse(null)

        fun formatTableSection(title: String, rows: List<BillTableDataDTO>): String {
            if (rows.isEmpty()) return "$title: Nenhum dado encontrado."
            return buildString {
                appendLine("$title:")
                rows.forEach {
                    appendLine("- ${it.billType}: ${it.billDescription} - R$ ${it.billValue} | Pago: ${if (it.paid) "Sim" else "Não"}")
                }
            }
        }
    }
}