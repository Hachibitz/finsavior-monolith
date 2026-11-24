package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.InstallmentGroupDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpdateInstallmentRequest
import br.com.finsavior.monolith.finsavior_monolith.service.InstallmentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("bill/installments")
class InstallmentController(
    private val installmentService: InstallmentService
) {

    @GetMapping
    fun listInstallments(): List<InstallmentGroupDTO> =
        installmentService.listInstallments()

    @PutMapping("/{id}")
    fun updateInstallment(
        @PathVariable id: Long,
        @Valid @RequestBody request: UpdateInstallmentRequest
    ): InstallmentGroupDTO =
        installmentService.updateInstallment(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteInstallment(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") deleteUnpaidOnly: Boolean
    ) =
        installmentService.deleteInstallment(id, deleteUnpaidOnly)
}
