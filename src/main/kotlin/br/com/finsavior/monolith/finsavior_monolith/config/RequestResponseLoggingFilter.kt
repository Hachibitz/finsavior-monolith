package br.com.finsavior.monolith.finsavior_monolith.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KLogger
import mu.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.nio.charset.Charset
import java.util.Locale
import java.util.UUID

@Component
class RequestResponseLoggingFilter : OncePerRequestFilter() {

    private val log: KLogger = KotlinLogging.logger {}

    companion object {
        private const val MAX_BODY_LOG_CHARS = 2_000
        private val SENSITIVE_HEADERS = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key"
        )
        private val SENSITIVE_JSON_FIELD_REGEX = Regex(
            "(?i)(\"(?:password|senha|token|accessToken|refreshToken|idToken|apiKey|secret|authorization|profilePicture)\"\\s*:\\s*\")([^\"]*)\""
        )
        private val QUERY_TOKEN_REGEX = Regex("(?i)(token|accessToken|refreshToken|idToken|password|senha|apiKey)=([^&]*)")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (!log.isDebugEnabled) {
            filterChain.doFilter(request, response)
            return
        }

        val requestId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val wrappedRequest = ContentCachingRequestWrapper(request)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } finally {
            val durationMs = System.currentTimeMillis() - startedAt
            log.debug {
                buildString {
                    append("http_request_response ")
                    append("requestId=$requestId ")
                    append("method=${request.method} ")
                    append("uri=${maskedUri(request)} ")
                    append("status=${wrappedResponse.status} ")
                    append("durationMs=$durationMs ")
                    append("remoteAddr=${request.remoteAddr} ")
                    append("userAgent=${request.getHeader("User-Agent")?.take(200)} ")
                    append("requestHeaders=${safeHeaders(request)} ")
                    append("requestBody=${safeRequestBody(wrappedRequest)} ")
                    append("responseBody=${safeResponseBody(wrappedResponse)}")
                }
            }
            wrappedResponse.copyBodyToResponse()
        }
    }

    private fun maskedUri(request: HttpServletRequest): String {
        val query = request.queryString?.replace(QUERY_TOKEN_REGEX) { "${it.groupValues[1]}=***" }
        return request.requestURI + (query?.let { "?$it" } ?: "")
    }

    private fun safeHeaders(request: HttpServletRequest): Map<String, String> =
        request.headerNames.asSequence().associateWith { headerName ->
            if (headerName.lowercase(Locale.getDefault()) in SENSITIVE_HEADERS) {
                "***"
            } else {
                request.getHeaders(headerName).toList().joinToString(",").take(500)
            }
        }

    private fun safeRequestBody(request: ContentCachingRequestWrapper): String {
        if (request.contentType?.lowercase(Locale.getDefault())?.startsWith("multipart/") == true) {
            return "[multipart body omitted]"
        }
        return request.contentAsByteArray.toSanitizedString(request.characterEncoding)
    }

    private fun safeResponseBody(response: ContentCachingResponseWrapper): String {
        val contentType = response.contentType?.lowercase(Locale.getDefault())
        if (contentType?.startsWith("application/octet-stream") == true || contentType?.startsWith("image/") == true) {
            return "[binary body omitted]"
        }
        return response.contentAsByteArray.toSanitizedString(response.characterEncoding)
    }

    private fun ByteArray.toSanitizedString(characterEncoding: String?): String {
        if (isEmpty()) return ""
        val charset = runCatching { Charset.forName(characterEncoding ?: Charsets.UTF_8.name()) }
            .getOrDefault(Charsets.UTF_8)
        return String(this, charset)
            .replace(SENSITIVE_JSON_FIELD_REGEX) { "${it.groupValues[1]}***\"" }
            .replace(Regex("\\s+"), " ")
            .take(MAX_BODY_LOG_CHARS)
    }
}
