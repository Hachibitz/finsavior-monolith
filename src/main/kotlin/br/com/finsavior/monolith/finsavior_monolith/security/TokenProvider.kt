package br.com.finsavior.monolith.finsavior_monolith.security

import br.com.finsavior.monolith.finsavior_monolith.repository.UserRepository
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import java.util.*

@Component
class TokenProvider(
    @Value("\${app.jwtSecret}") private val jwtSecret: String,
    @Value("\${app.jwtExpirationMs}") private val jwtExpirationMs: Int,
    @Value("\${app.jwtExpirationMsForRememberMe}") private val jwtExpirationMsForRememberMe: Long,
    private val userSecurityDetails: UserSecurityDetails,
    private val userRepository: UserRepository
) {

    fun generateToken(authentication: Authentication): String {
        val customUserDetails = userSecurityDetails.loadUserByUsername(authentication.principal.toString()) as CustomUserDetails
        val now = Date()
        val expiryDate = Date(now.time + jwtExpirationMs)

        return Jwts.builder()
            .claim("username", customUserDetails.username)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(SignatureAlgorithm.HS512, jwtSecret)
            .compact()
    }

    fun generateRefreshToken(username: String, isRememberMe: Boolean): String {
        val now = Date()
        val expiryDate = if (isRememberMe) {
            Date(now.time + jwtExpirationMsForRememberMe)
        } else {
            Date(now.time + 900000) // 15 minutos
        }

        return Jwts.builder()
            .claim("username", username)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(SignatureAlgorithm.HS512, jwtSecret)
            .compact()
    }

    fun getUsernameFromToken(token: String): String {
        val claims = Jwts.parser().setSigningKey(jwtSecret).build().parseClaimsJws(token).body
        return claims["username"] as String
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser().setSigningKey(jwtSecret).build().parseClaimsJws(token)
            val username = getUsernameFromToken(token)
            val user = userRepository.findByUsername(username)
            user != null
        } catch (e: Exception) {
            false
        }
    }
}
