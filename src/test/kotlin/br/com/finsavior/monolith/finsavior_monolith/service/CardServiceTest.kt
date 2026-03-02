package br.com.finsavior.monolith.finsavior_monolith.service

import br.com.finsavior.monolith.finsavior_monolith.exception.CardDeletionException
import br.com.finsavior.monolith.finsavior_monolith.exception.CardNotFoundException
import br.com.finsavior.monolith.finsavior_monolith.exception.CardUnauthorizedException
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardRegisterDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.CardUpdateDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.Card
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CardStyleEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.CardRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import java.util.*

class CardServiceTest {

    @Mock
    private lateinit var cardRepository: CardRepository

    @Mock
    private lateinit var userService: UserService

    @Mock
    private lateinit var billTableDataRepository: BillTableDataRepository

    private lateinit var cardService: CardService

    private lateinit var user: User

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        cardService = CardService(cardRepository, userService, billTableDataRepository)

        user = User(
            id = 1L,
            username = "testuser",
            password = "pass",
            firstName = "Test",
            lastName = "User",
            email = "test@test.com",
            userPlan = null,
            userProfile = null,
            roles = mutableSetOf(),
            enabled = true,
            audit = null
        )
    }

    @Test
    fun `listUserCards should return list of cards for authenticated user`() {
        val card1 = Card(id = 1L, name = "Card 1", style = CardStyleEnum.INDIGO_BLUE, user = user, audit = null)
        val card2 = Card(id = 2L, name = "Card 2", style = CardStyleEnum.ROSE_PINK, user = user, audit = null)

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(cardRepository.findByUserId(user.id!!)).thenReturn(listOf(card1, card2))

        val result = cardService.listUserCards()

        assertEquals(2, result.size)
        assertEquals("Card 1", result[0].name)
        assertEquals("Card 2", result[1].name)
    }

    @Test
    fun `registerCard should create default Principal card if user has none`() {
        val request = CardRegisterDTO(name = "My Card", style = CardStyleEnum.SLATE_DARK)
        val principalCard = Card(id = 1L, name = "Principal", style = null, user = user, audit = null)
        val newCard = Card(id = 2L, name = "My Card", style = CardStyleEnum.SLATE_DARK, user = user, audit = null)

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(cardRepository.findByUserId(user.id!!)).thenReturn(emptyList())
        whenever(cardRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(principalCard, newCard)

        val result = cardService.registerCard(request)

        assertEquals("My Card", result.name)
    }

    @Test
    fun `updateCard should modify card name and style`() {
        val card = Card(id = 1L, name = "Old", style = CardStyleEnum.SLATE_DARK, user = user, audit = null)
        val request = CardUpdateDTO(id = 1L, name = "Updated", style = CardStyleEnum.EMERALD_TEAL)

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(cardRepository.findById(1L)).thenReturn(Optional.of(card))
        whenever(cardRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(
            Card(id = 1L, name = "Updated", style = CardStyleEnum.EMERALD_TEAL, user = user, audit = null)
        )

        val result = cardService.updateCard(request)

        assertEquals("Updated", result.name)
        assertEquals(CardStyleEnum.EMERALD_TEAL, result.style)
    }

    @Test
    fun `updateCard should throw CardNotFoundException when card does not exist`() {
        val request = CardUpdateDTO(id = 999L, name = "Updated", style = CardStyleEnum.EMERALD_TEAL)

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(cardRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows(CardNotFoundException::class.java) {
            cardService.updateCard(request)
        }
    }

    @Test
    fun `updateCard should throw CardUnauthorizedException when card belongs to different user`() {
        val otherUser = User(id = 2L, username = "other", password = "pass", firstName = "Other", lastName = "User", email = "other@test.com", userPlan = null, userProfile = null, roles = mutableSetOf(), enabled = true, audit = null)
        val card = Card(id = 1L, name = "Other's Card", style = CardStyleEnum.SLATE_DARK, user = otherUser, audit = null)
        val request = CardUpdateDTO(id = 1L, name = "Updated", style = CardStyleEnum.EMERALD_TEAL)

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(cardRepository.findById(1L)).thenReturn(Optional.of(card))

        assertThrows(CardUnauthorizedException::class.java) {
            cardService.updateCard(request)
        }
    }

    @Test
    fun `deleteCard should throw CardDeletionException when card has linked expenses`() {
        val card = Card(id = 1L, name = "My Card", style = CardStyleEnum.SLATE_DARK, user = user, audit = null)
        val billData = br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData(
            id = 1L,
            userId = user.id!!,
            billType = "My Card",
            billDate = "Feb 2026",
            billName = "Test",
            billValue = java.math.BigDecimal("100.00"),
            billTable = BillTableEnum.CREDIT_CARD,
            isPaid = false,
            audit = null
        )

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(cardRepository.findById(1L)).thenReturn(Optional.of(card))
        whenever(billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(user.id!!, "%", BillTableEnum.CREDIT_CARD)).thenReturn(listOf(billData))

        assertThrows(CardDeletionException::class.java) {
            cardService.deleteCard(1L)
        }
    }

    @Test
    fun `deleteCard should succeed when no linked expenses exist`() {
        val card = Card(id = 1L, name = "My Card", style = CardStyleEnum.SLATE_DARK, user = user, audit = null)

        whenever(userService.getUserByContext()).thenReturn(user)
        whenever(cardRepository.findById(1L)).thenReturn(Optional.of(card))
        whenever(billTableDataRepository.findAllByUserIdAndBillDateAndBillTable(user.id!!, "%", BillTableEnum.CREDIT_CARD)).thenReturn(emptyList())

        cardService.deleteCard(1L)

        org.mockito.Mockito.verify(cardRepository).delete(card)
    }
}

