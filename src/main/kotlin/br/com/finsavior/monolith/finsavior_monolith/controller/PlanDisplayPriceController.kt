package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanDisplayPriceDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.PlanPricesResponse
import br.com.finsavior.monolith.finsavior_monolith.model.dto.UpsertPlanDisplayPriceRequest
import br.com.finsavior.monolith.finsavior_monolith.service.PlanDisplayPriceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/plans")
class PlanDisplayPriceController(
    private val planDisplayPriceService: PlanDisplayPriceService
) {

    /**
     * Public display prices for the Plans screen.
     * Prefer ?currency=USD; otherwise maps Accept-Language → currency (fallback BRL).
     */
    @GetMapping("/display-prices")
    fun getDisplayPrices(
        @RequestParam(required = false) currency: String?,
        @RequestHeader(value = "Accept-Language", required = false) acceptLanguage: String?
    ): PlanPricesResponse =
        planDisplayPriceService.getPricesForCurrency(currency, acceptLanguage)
}

@RestController
@RequestMapping("/admin/plan-prices")
class AdminPlanDisplayPriceController(
    private val planDisplayPriceService: PlanDisplayPriceService
) {

    @GetMapping
    fun listAll(): List<PlanDisplayPriceDTO> = planDisplayPriceService.listAll()

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    fun upsert(@Valid @RequestBody request: UpsertPlanDisplayPriceRequest): PlanDisplayPriceDTO =
        planDisplayPriceService.upsert(request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        planDisplayPriceService.delete(id)
    }
}
