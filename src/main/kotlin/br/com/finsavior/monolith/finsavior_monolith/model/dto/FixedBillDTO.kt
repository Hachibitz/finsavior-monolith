package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FixedBillGenerationStrategyEnum
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class FixedBillDTO(
    val id: Long,
    val billName: String,
    val billValue: BigDecimal,
    val billDescription: String?,
    val billCategory: String?,
    val billType: BillTypeEnum,
    val billTable: BillTableEnum,
    val paymentType: String?,
    val cardId: String?,
    val dayOfMonth: Int?,
    val startBillDate: String,
    val generationStrategy: FixedBillGenerationStrategyEnum,
    val active: Boolean,
    val instanceCount: Int,
    val paidCount: Int
)

data class UpdateFixedBillRequest(
    @field:NotBlank(message = "O título é obrigatório")
    @field:Size(max = 100, message = "O título deve ter no máximo 100 caracteres")
    val billName: String,

    @field:NotNull
    @field:DecimalMin(value = "0.0", inclusive = true, message = "O valor não pode ser negativo")
    @field:DecimalMax(value = "9999999999.99", message = "Valor acima do permitido")
    @field:Digits(integer = 10, fraction = 2, message = "Valor inválido")
    val billValue: BigDecimal,

    @field:Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
    val billDescription: String? = null,

    @field:Size(max = 60, message = "Categoria inválida")
    val billCategory: String? = null,

    val billTable: BillTableEnum? = null,

    @field:Size(max = 64, message = "Cartão inválido")
    val cardId: String? = null,

    @field:Min(1)
    @field:Max(31)
    val dayOfMonth: Int? = null,

    val generationStrategy: FixedBillGenerationStrategyEnum? = null,

    val active: Boolean? = null,

    val applyToFutureOpenBills: Boolean = true
)

data class MigrateFixedBillStrategyRequest(
    @field:NotNull
    val generationStrategy: FixedBillGenerationStrategyEnum
)

data class ToggleFixedBillActiveRequest(
    @field:NotNull
    val active: Boolean
)
