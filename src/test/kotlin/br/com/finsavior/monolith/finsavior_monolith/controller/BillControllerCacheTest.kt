@file:Suppress("DEPRECATION")

package br.com.finsavior.monolith.finsavior_monolith.controller

// Mockito verify removed to avoid deprecated @SpyBean usage; using repository directly
import br.com.finsavior.monolith.finsavior_monolith.model.dto.BillTableDataDTO
import br.com.finsavior.monolith.finsavior_monolith.model.entity.BillTableData
import br.com.finsavior.monolith.finsavior_monolith.model.entity.User
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTableEnum
import br.com.finsavior.monolith.finsavior_monolith.model.enums.BillTypeEnum
import br.com.finsavior.monolith.finsavior_monolith.repository.BillTableDataRepository
import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@Suppress("DEPRECATION")
class BillControllerCacheTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var billTableDataRepository: BillTableDataRepository

    private lateinit var testUser: User

    @BeforeEach
    fun setup() {
        cacheManager.cacheNames.forEach { cacheManager.getCache(it)?.clear() }
        
        testUser = userRepository.findByUsername("testuser") ?: userRepository.save(User(
            username = "testuser",
            email = "test@cache.com",
            password = "password",
            firstName = "Test",
            lastName = "User"
        ))
    }

    @Test
    @WithMockUser(username = "testuser")
    fun `when loading data twice, second call should hit cache`() {
        mockMvc.perform(get("/api/bill/load-main-table-data?billDate=Apr 2026"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/bill/load-main-table-data?billDate=Apr 2026"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "testuser")
    fun `when new bill is created, cache should be evicted`() {
        mockMvc.perform(get("/api/bill/load-main-table-data?billDate=May 2026"))
            .andExpect(status().isOk)

        val newBill = BillTableDataDTO(
            id = null, userId = testUser.id!!, billType = BillTypeEnum.EXPENSE, billDate = "May 2026", billName = "New Bill",
            billValue = BigDecimal.TEN, billDescription = "Test", billTable = BillTableEnum.MAIN, paid = false, billCategory = "Test"
        )
        mockMvc.perform(post("/api/bill/bill-register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(newBill)))
            .andExpect(status().isCreated)

        mockMvc.perform(get("/api/bill/load-main-table-data?billDate=May 2026"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "testuser")
    fun `when bill is edited, cache should be evicted`() {
        val billToEdit = billTableDataRepository.save(BillTableData(
            id = 0L,
            userId = testUser.id!!,
            billType = BillTypeEnum.EXPENSE,
            billDate = "Jul 2026",
            billName = "ToEdit",
            billValue = BigDecimal.ONE,
            billTable = BillTableEnum.MAIN,
            billCategory = "Alimentação"
        ))

        mockMvc.perform(get("/api/bill/load-main-table-data?billDate=Jul 2026"))
            .andExpect(status().isOk)

        val updatedBill = BillTableDataDTO(
            id = billToEdit.id, userId = testUser.id!!, billType = BillTypeEnum.EXPENSE, billDate = "Jul 2026", billName = "Edited Bill",
            billValue = BigDecimal.TEN, billDescription = "Updated", billTable = BillTableEnum.MAIN, paid = true, billCategory = "Updated"
        )
        mockMvc.perform(put("/api/bill/edit")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updatedBill)))
            .andExpect(status().isCreated)

        mockMvc.perform(get("/api/bill/load-main-table-data?billDate=Jul 2026"))
            .andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "testuser")
    fun `when bill is deleted, cache should be evicted`() {
        val billToDelete = billTableDataRepository.save(BillTableData(
            id = 0L,
            userId = testUser.id!!,
            billType = BillTypeEnum.EXPENSE,
            billDate = "Jun 2026",
            billName = "ToDelete",
            billValue = BigDecimal.ONE,
            billTable = BillTableEnum.MAIN,
            billCategory = "Alimentação"
        ))

        mockMvc.perform(get("/api/bill/load-main-table-data?billDate=Jun 2026"))
            .andExpect(status().isOk)

        mockMvc.perform(delete("/api/bill/delete?itemId=${billToDelete.id}"))
            .andExpect(status().isOk)

        mockMvc.perform(get("/api/bill/load-main-table-data?billDate=Jun 2026"))
            .andExpect(status().isOk)
    }
}
