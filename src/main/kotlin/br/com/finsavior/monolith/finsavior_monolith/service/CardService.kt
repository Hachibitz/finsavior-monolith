package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.CardNotFoundException
import br.com.finsavior.monolith.finsavior_monolith.exception.CardUnauthorizedException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardRegisterDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardUpdateDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Card
import br.com.finsavior.monolith.finsavior_monolith.model.mapper.toDTO
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.CardRepository
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CardService(
    private val cardRepository: CardRepository,
    private val userService: UserService,
    private val billTableDataRepository: BillTableDataRepository
) {

    private val log: KLogger = KotlinLogging.logger { }

    @Transactional(readOnly = true)
    fun listUserCards(): List<CardDTO> {
        val user = userService.getUserByContext()
        return cardRepository.findByUserId(user.id!!)
            .map { it.toDTO() }
    }

    @Transactional
    fun registerCard(request: CardRegisterDTO): CardDTO {
        val user = userService.getUserByContext()

        val existing = cardRepository.findByUserId(user.id!!)
        if (existing.isEmpty()) {
            val principal = Card(name = "Principal", style = null, user = user)
            cardRepository.save(principal)
        }

        val card = Card(name = request.name, style = request.style, user = user)
        val saved = cardRepository.save(card)
        log.info("Created card id=${saved.id} for user=${user.id}")
        return saved.toDTO()
    }

    @Transactional
    fun updateCard(request: CardUpdateDTO): CardDTO {
        val user = userService.getUserByContext()
        val card = cardRepository.findById(request.id)
            .orElseThrow { CardNotFoundException("Card with id ${request.id} not found") }

        if (card.user?.id != user.id) {
            throw CardUnauthorizedException("Card does not belong to current user")
        }

        card.name = request.name
        card.style = request.style
        val saved = cardRepository.save(card)
        log.info("Updated card id=${saved.id} for user=${user.id}")
        return saved.toDTO()
    }

    @Transactional
    fun deleteCard(cardId: Long) {
        val user = userService.getUserByContext()
        val card = cardRepository.findById(cardId)
            .orElseThrow { CardNotFoundException("Card with id $cardId not found") }

        if (card.user?.id != user.id) {
            throw CardUnauthorizedException("Card does not belong to current user")
        }

        billTableDataRepository.deleteByCardId(cardId.toString())
        cardRepository.delete(card)
        log.info("Deleted card id=$cardId and associated bills for user=${user.id}")
    }
}
