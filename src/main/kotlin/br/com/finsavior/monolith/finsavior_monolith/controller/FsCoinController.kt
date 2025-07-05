package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.service.FsCoinService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("fscoin")
class FsCoinController(
    private val fsCoinService: FsCoinService
) {

    @GetMapping("/balance")
    @ResponseStatus(HttpStatus.OK)
    fun getBalance(): Long =
        fsCoinService.getBalance()

    @PostMapping("/earn")
    @ResponseStatus(HttpStatus.OK)
    fun earnCoins(): Long =
        fsCoinService.earnCoins()
}
