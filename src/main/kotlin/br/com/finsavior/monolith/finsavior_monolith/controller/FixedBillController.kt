package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.FixedBillDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.MigrateFixedBillStrategyRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ToggleFixedBillActiveRequest
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpdateFixedBillRequest
import br.com.finsavior.monolith.finsavior_monolith.service.FixedBillService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("bill/fixed")
class FixedBillController(
    private val fixedBillService: FixedBillService
) {

    @GetMapping
    fun listFixedBills(): List<FixedBillDTO> =
        fixedBillService.listFixedBills()

    @PutMapping("/{id}")
    fun updateFixedBill(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateFixedBillRequest
    ): FixedBillDTO =
        fixedBillService.updateFixedBill(id, request)

    @PatchMapping("/{id}/strategy")
    fun migrateStrategy(
        @PathVariable id: Long,
        @Valid @RequestBody request: MigrateFixedBillStrategyRequest
    ): FixedBillDTO =
        fixedBillService.migrateStrategy(id, request.generationStrategy)

    @PatchMapping("/{id}/active")
    fun toggleActive(
        @PathVariable id: Long,
        @Valid @RequestBody request: ToggleFixedBillActiveRequest
    ): FixedBillDTO =
        fixedBillService.toggleActive(id, request.active)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteFixedBill(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") deleteFutureOnly: Boolean
    ) =
        fixedBillService.deleteFixedBill(id, deleteFutureOnly)
}
