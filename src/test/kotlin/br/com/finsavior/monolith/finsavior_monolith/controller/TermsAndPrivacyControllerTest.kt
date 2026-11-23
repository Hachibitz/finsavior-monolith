package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.service.TermsAndPrivacyService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType

class TermsAndPrivacyControllerTest {
    @Test
    fun `returns styled html for browser accept header and markdown for api clients`() {
        val service = mock<TermsAndPrivacyService>()
        whenever(service.getTerms()).thenReturn("# Termos\n\n* **1.1.** Teste com <script>alert(1)</script>")

        val controller = TermsAndPrivacyController(service)

        val browserResponse = controller.getTerms("text/html,application/xhtml+xml")
        assertEquals(MediaType("text", "html", Charsets.UTF_8), browserResponse.headers.contentType)
        assertTrue(browserResponse.body!!.contains("<!DOCTYPE html>"))
        assertTrue(browserResponse.body!!.contains("<h1>Termos</h1>"))
        assertTrue(browserResponse.body!!.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))

        val apiResponse = controller.getTerms("application/json, text/plain, */*")
        assertEquals(MediaType("text", "plain", Charsets.UTF_8), apiResponse.headers.contentType)
        assertTrue(apiResponse.body!!.startsWith("# Termos"))
    }
}
