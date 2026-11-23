package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.service.TermsAndPrivacyService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils

@RestController
@RequestMapping("/terms-and-privacy")
class TermsAndPrivacyController(
    private val termsAndPrivacyService: TermsAndPrivacyService
) {

    @GetMapping("/get-terms")
    fun getTerms(
        @RequestHeader(value = HttpHeaders.ACCEPT, required = false) accept: String?
    ): ResponseEntity<String> =
        respond(termsAndPrivacyService.getTerms(), "Termos e Condições de Uso — FinSavior", accept)

    @GetMapping("/get-privacy-policy")
    fun getPrivacyPolicy(
        @RequestHeader(value = HttpHeaders.ACCEPT, required = false) accept: String?
    ): ResponseEntity<String> =
        respond(termsAndPrivacyService.getPrivacyPolicy(), "Política de Privacidade — FinSavior", accept)

    private fun respond(markdownContent: String, pageTitle: String, accept: String?): ResponseEntity<String> {
        val wantsHtml = accept?.contains("text/html", ignoreCase = true) == true
        if (!wantsHtml) {
            return ResponseEntity.status(HttpStatus.OK)
                .contentType(MediaType("text", "plain", Charsets.UTF_8))
                .body(markdownContent)
        }
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType("text", "html", Charsets.UTF_8))
            .body(renderHtmlDocument(pageTitle, markdownContent))
    }

    private fun renderHtmlDocument(title: String, markdown: String): String {
        val bodyHtml = markdownToSafeHtml(markdown)
        val safeTitle = HtmlUtils.htmlEscape(title)
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1.0" />
              <title>$safeTitle</title>
              <style>
                :root { color-scheme: dark; }
                body {
                  margin: 0;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Inter, sans-serif;
                  background: #0b1120;
                  color: #e2e8f0;
                  line-height: 1.65;
                  padding: 32px 16px;
                }
                main {
                  max-width: 820px;
                  margin: 0 auto;
                  background: #111a2e;
                  border: 1px solid rgba(148, 163, 184, 0.18);
                  border-radius: 18px;
                  padding: 36px 32px;
                  box-shadow: 0 20px 50px rgba(0, 0, 0, 0.4);
                }
                h1 { font-size: 1.75rem; color: #ffffff; margin: 0 0 20px; border-bottom: 1px solid rgba(148, 163, 184, 0.2); padding-bottom: 12px; }
                h2 { font-size: 1.2rem; color: #38bdf8; margin: 28px 0 10px; }
                h3 { font-size: 1.05rem; color: #cbd5e1; margin: 18px 0 8px; }
                p { margin: 10px 0; color: #cbd5e1; }
                ul { margin: 8px 0 14px 20px; padding: 0; }
                li { margin: 6px 0; color: #cbd5e1; }
                strong { color: #f8fafc; }
                a { color: #38bdf8; text-decoration: none; }
                a:hover { text-decoration: underline; }
              </style>
            </head>
            <body>
              <main>
                $bodyHtml
              </main>
            </body>
            </html>
        """.trimIndent()
    }

    private fun markdownToSafeHtml(markdown: String): String {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        val sb = StringBuilder()
        var inList = false

        fun closeList() {
            if (inList) {
                sb.append("</ul>\n")
                inList = false
            }
        }

        fun formatInline(raw: String): String {
            var escaped = HtmlUtils.htmlEscape(raw)
            escaped = Regex("""\*\*(.+?)\*\*""").replace(escaped) { "<strong>${it.groupValues[1]}</strong>" }
            escaped = Regex("""([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,})""").replace(escaped) {
                "<a href=\"mailto:${it.groupValues[1]}\">${it.groupValues[1]}</a>"
            }
            return escaped
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.isEmpty() -> closeList()
                line.startsWith("### ") -> {
                    closeList()
                    sb.append("<h3>").append(formatInline(line.removePrefix("### "))).append("</h3>\n")
                }
                line.startsWith("## ") -> {
                    closeList()
                    sb.append("<h2>").append(formatInline(line.removePrefix("## "))).append("</h2>\n")
                }
                line.startsWith("# ") -> {
                    closeList()
                    sb.append("<h1>").append(formatInline(line.removePrefix("# "))).append("</h1>\n")
                }
                line.startsWith("* ") || line.startsWith("- ") -> {
                    if (!inList) {
                        sb.append("<ul>\n")
                        inList = true
                    }
                    sb.append("<li>").append(formatInline(line.substring(2))).append("</li>\n")
                }
                else -> {
                    closeList()
                    sb.append("<p>").append(formatInline(line)).append("</p>\n")
                }
            }
        }
        closeList()
        return sb.toString()
    }
}