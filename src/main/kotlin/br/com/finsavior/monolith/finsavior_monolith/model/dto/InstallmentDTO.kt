package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class InstallmentGroupDTO(
    val id: Long,
    val billName: String,
    val billValue: BigDecimal,
    val totalValue: BigDecimal,
    val remainingValue: BigDecimal,
    val billCategory: String?,
    val billTable: BillTableEnum,
    val cardId: String?,
    val totalInstallments: Int,
    val paidCount: Int,
    val remainingCount: Int,
    val startBillDate: String,
    val endBillDate: String,
    val bills: List<BillTableDataDTO>
)

data class UpdateInstallmentRequest(
    @field:NotBlank(message = "O título é obrigatório")
    @field:Size(max = 100, message = "O título deve ter no máximo 100 caracteres")
    val billName: String,

    @field:NotNull
    @field:DecimalMin(value = "0.0", inclusive = true, message = "O valor não pode ser negativo")
    @field:DecimalMax(value = "9999999999.99", message = "Valor acima do permitido")
    @field:Digits(integer = 10, fraction = 2, message = "Valor inválido")
    val billValue: BigDecimal,

    @field:Size(max = 60, message = "Categoria inválida")
    val billCategory: String? = null,

    val billTable: BillTableEnum? = null,

    @field:Size(max = 64, message = "Cartão inválido")
    val cardId: String? = null,

    val applyToUnpaidOnly: Boolean = true
)
