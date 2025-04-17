package br.com.finsavior.monolith.finsavior_monolith.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JWTAuthenticationFilter(
    private val tokenProvider: TokenProvider,
    private val userSecurityDetails: UserSecurityDetails
) : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val token = extractTokenFromRequest(request)
        if (token != null && tokenProvider.validateToken(token)) {
            val username = tokenProvider.getUsernameFromToken(token)
            val userDetails = userSecurityDetails.loadUserByUsername(username)
            val authenticationToken = UsernamePasswordAuthenticationToken(userDetails, null, userDetails.authorities)
            authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request)

            SecurityContextHolder.getContext().authentication = authenticationToken
        }
        filterChain.doFilter(request, response)
    }

    private fun extractTokenFromRequest(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7)
        }

        val authParam = request.getParameter("auth")
        return if (!authParam.isNullOrBlank()) {
            authParam
        } else {
            null
        }
    }
}
