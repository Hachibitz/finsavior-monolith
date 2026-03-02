package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardRegisterDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardUpdateDTO
import br.com.finsavior.monolith.finsavior_monolith.service.CardService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("card")
class CardController(
    private val cardService: CardService
) {

    @GetMapping("/list")
    @ResponseStatus(HttpStatus.OK)
    fun listCards(): List<CardDTO> =
        cardService.listUserCards()

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun registerCard(@Valid @RequestBody request: CardRegisterDTO): CardDTO =
        cardService.registerCard(request)

    @PutMapping("/update")
    @ResponseStatus(HttpStatus.OK)
    fun updateCard(@Valid @RequestBody request: CardUpdateDTO): CardDTO =
        cardService.updateCard(request)

    @DeleteMapping("/delete/{cardId}")
    @ResponseStatus(HttpStatus.OK)
    fun deleteCard(@PathVariable cardId: Long) =
        cardService.deleteCard(cardId)
}
