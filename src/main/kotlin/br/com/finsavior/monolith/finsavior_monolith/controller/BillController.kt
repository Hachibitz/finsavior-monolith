package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.service.BillService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("bill")
class BillController(
    var service: BillService
) {

    @PostMapping("/bill-register")
    @ResponseStatus(HttpStatus.CREATED)
    fun billRegister(@Valid @RequestBody billTableDataDTO: BillTableDataDTO) =
        service.billRegister(billTableDataDTO)

    @GetMapping("/load-main-table-data")
    fun loadMainTableData(@RequestParam billDate: String): List<BillTableDataDTO> =
        service.loadMainTableData(billDate)

    @GetMapping("/load-card-table-data")
    fun loadCardTableData(@RequestParam billDate: String): List<BillTableDataDTO> =
        service.loadCardTableData(billDate)

    @GetMapping("/load-assets-table-data")
    fun loadAssetsTableData(@RequestParam billDate: String): List<BillTableDataDTO> =
        service.loadAssetsTableData(billDate)

    @GetMapping("/load-payment-card-table-data")
    fun loadPaymentCardTableData(@RequestParam billDate: String): List<BillTableDataDTO> =
        service.loadPaymentCardTableData(billDate)

    @DeleteMapping("/delete")
    @ResponseStatus(HttpStatus.OK)
    fun deleteItemFromMainTable(@RequestParam itemId: Long) =
        service.deleteItemFromTable(itemId)

    @PutMapping("/edit")
    @ResponseStatus(HttpStatus.CREATED)
    fun editItemFromCardTable(@RequestBody billTableDataDTO: BillTableDataDTO) =
        service.billUpdate(billTableDataDTO)

    @PostMapping("/batch-register")
    @ResponseStatus(HttpStatus.CREATED)
    fun batchRegister(@RequestBody bills: List<BillTableDataDTO>) =
        service.batchRegister(bills)

}