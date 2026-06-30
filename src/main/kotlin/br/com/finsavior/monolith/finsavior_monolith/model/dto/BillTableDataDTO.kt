package br.com.finsavior.monolith.finsavior_monolith.model.dto

import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillEntryMethodEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.FixedBillGenerationStrategyEnum
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class BillTableDataDTO (
    var id: Long? = null,
    var userId: Long,
    @field:NotNull
    var billType: BillTypeEnum,
    @field:NotBlank
    @field:Size(max = 20, message = "Data inválida")
    var billDate: String,
    @field:NotBlank(message = "O título é obrigatório")
    @field:Size(max = 100, message = "O título deve ter no máximo 100 caracteres")
    var billName: String,
    @field:NotNull
    @field:DecimalMin(value = "0.0", inclusive = true, message = "O valor não pode ser negativo")
    @field:DecimalMax(value = "9999999999.99", message = "Valor acima do permitido")
    @field:Digits(integer = 10, fraction = 2, message = "Valor inválido")
    var billValue: BigDecimal,
    @field:Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres")
    var billDescription: String?,
    @field:NotNull
    var billTable: BillTableEnum,
    @field:Size(max = 60, message = "Categoria inválida")
    var billCategory: String? = null,
    var paid: Boolean,
    var isInstallment: Boolean? = false,
    var installmentCount: Int? = null,
    var currentInstallment: Int? = null,
    var entryMethod: BillEntryMethodEnum = BillEntryMethodEnum.MANUAL,
    var isRecurrent: Boolean? = null,
    @field:Size(max = 30, message = "Tipo de pagamento inválido")
    var paymentType: String? = null,
    @field:Size(max = 64, message = "Cartão inválido")
    var cardId: String? = null,
    /** Real purchase/bill date in ISO format (yyyy-MM-dd). Optional. */
    @field:Size(max = 10, message = "Data de compra inválida")
    var purchaseDate: String? = null,
    var fixedBillGenerationStrategy: FixedBillGenerationStrategyEnum? = null,
    /** Read-only: id of the parent fixed bill, when this row is a recurring instance. */
    var fixedBillId: Long? = null,
)
