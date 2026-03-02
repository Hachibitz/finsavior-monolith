package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.CardStyleEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.CardRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringBootTest
@AutoConfigureMockMvc
class CardControllerIT {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var userRepository: UserRepository

    @Autowired
    lateinit var cardRepository: CardRepository

    lateinit var user: User

    @BeforeEach
    fun setup() {
        cardRepository.deleteAll()
        userRepository.deleteAll()
        user = User(
            username = "test-user",
            password = "x",
            firstName = "Test",
            lastName = "User",
            email = "test@example.com",
            userPlan = null,
            userProfile = null,
            roles = mutableSetOf(),
            enabled = true,
            audit = null
        )
        user = userRepository.save(user)

        // populate security context so UserService.getUserByContext() resolves the saved user
        val auth = UsernamePasswordAuthenticationToken(user.username, null, listOf())
        SecurityContextHolder.getContext().authentication = auth
    }

    @Test
    fun `register card should create card and return dto`() {
        val json = "{\"name\":\"My Card\",\"style\":\"INDIGO_BLUE\"}"

        mockMvc.perform(
            MockMvcRequestBuilders.post("/card/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
        )
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("My Card"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.style").value("INDIGO_BLUE"))

        val total = cardRepository.findByUserId(user.id!!).size
        // principal + created
        assertEquals(2, total)
    }

    @Test
    fun `list cards should return existing cards`() {
        // create a card directly
        val card = br.com.finsavior.monolith.finsavior_monolith.model.entity.Card(
            name = "C1",
            style = CardStyleEnum.SLATE_DARK,
            user = user,
            audit = null
        )
        cardRepository.save(card)

        mockMvc.perform(MockMvcRequestBuilders.get("/card/list"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].name").value("C1"))
            .andExpect(MockMvcResultMatchers.jsonPath("$[0].style").value("SLATE_DARK"))
    }

    @Test
    fun `update card should modify card and return updated dto`() {
        val card = br.com.finsavior.monolith.finsavior_monolith.model.entity.Card(
            name = "Old Name",
            style = CardStyleEnum.SLATE_DARK,
            user = user,
            audit = null
        )
        val saved = cardRepository.save(card)

        val updateJson = "{\"id\":${saved.id},\"name\":\"New Name\",\"style\":\"ROSE_PINK\"}"

        mockMvc.perform(
            MockMvcRequestBuilders.put("/card/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.name").value("New Name"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.style").value("ROSE_PINK"))

        val updated = cardRepository.findById(saved.id!!).get()
        assertEquals("New Name", updated.name)
        assertEquals(CardStyleEnum.ROSE_PINK, updated.style)
    }

    @Test
    fun `delete card should remove card from database`() {
        val card = br.com.finsavior.monolith.finsavior_monolith.model.entity.Card(
            name = "To Delete",
            style = CardStyleEnum.EMERALD_TEAL,
            user = user,
            audit = null
        )
        val saved = cardRepository.save(card)
        val cardId = saved.id!!

        mockMvc.perform(MockMvcRequestBuilders.delete("/card/delete/$cardId"))
            .andExpect(MockMvcResultMatchers.status().isOk)

        val exists = cardRepository.findById(cardId).isPresent
        assertEquals(false, exists)
    }

    @Test
    fun `delete card not belonging to user should return 403`() {
        // create another user
        val otherUser = User(
            username = "other-user",
            password = "x",
            firstName = "Other",
            lastName = "User",
            email = "other@example.com",
            userPlan = null,
            userProfile = null,
            roles = mutableSetOf(),
            enabled = true,
            audit = null
        )
        val savedOtherUser = userRepository.save(otherUser)

        // create card for other user
        val card = br.com.finsavior.monolith.finsavior_monolith.model.entity.Card(
            name = "Other's Card",
            style = CardStyleEnum.VIOLET_PURPLE,
            user = savedOtherUser,
            audit = null
        )
        val saved = cardRepository.save(card)

        mockMvc.perform(MockMvcRequestBuilders.delete("/card/delete/${saved.id}"))
            .andExpect(MockMvcResultMatchers.status().isForbidden)
    }

    @Test
    fun `update non-existent card should return 404`() {
        val updateJson = "{\"id\":99999,\"name\":\"New Name\",\"style\":\"ROSE_PINK\"}"

        mockMvc.perform(
            MockMvcRequestBuilders.put("/card/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson)
        )
            .andExpect(MockMvcResultMatchers.status().isNotFound)
    }
}
