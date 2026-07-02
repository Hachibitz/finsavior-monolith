package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.LoginRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.PasswordRecoveryRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ResetPasswordDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.SignUpDTO
import br.com.finsavior.monolith.finsavior_monolith.service.AuthenticationService
import br.com.finsavior.monolith.finsavior_monolith.service.RateLimitService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

@RestController
@RequestMapping("/auth")
class AuthController(
    private val authenticationService: AuthenticationService,
    private val rateLimitService: RateLimitService
) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@RequestBody signUpDTO: SignUpDTO, request: HttpServletRequest): ResponseEntity<*> {
        enforceRateLimit("signup:${clientIp(request)}", 5, Duration.ofHours(1))
        return authenticationService.signUp(signUpDTO)
    }

    @PostMapping("/login-auth")
    fun login(
        @RequestBody loginRequest: LoginRequestDTO,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        enforceRateLimit("login:${clientIp(request)}", 10, Duration.ofMinutes(5))
        return authenticationService.login(loginRequest, request, response)
    }

    @PostMapping("/validate-login")
    @ResponseStatus(HttpStatus.OK)
    fun login(
        @RequestBody loginRequest: LoginRequestDTO
    ) =
        authenticationService.validateLogin(loginRequest)

    @PostMapping("/login-google")
    fun loginWithGoogle(
        @RequestBody idTokenString: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        enforceRateLimit("login-google:${clientIp(request)}", 10, Duration.ofMinutes(5))
        return authenticationService.loginWithGoogle(idTokenString, request, response)
    }

    @PostMapping("/register-google")
    fun signUpWithGoogle(
        @RequestBody idTokenString: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        enforceRateLimit("register-google:${clientIp(request)}", 10, Duration.ofMinutes(5))
        return authenticationService.registerWithGoogle(idTokenString, request, response)
    }

    /**
     * Token can be sent via Authorization header (preferred — query strings leak
     * into proxy logs / browser history) or the legacy `token` query param.
     */
    @GetMapping("/validate-token")
    fun validateToken(
        @RequestParam(required = false) token: String?,
        @RequestHeader(value = "Authorization", required = false) authorization: String?
    ): ResponseEntity<Boolean?> {
        val effectiveToken = authorization?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotBlank() } ?: token
        return authenticationService.validateToken(effectiveToken)
    }

    @PostMapping("/refresh-token")
    fun refreshToken(
        @RequestBody(required = false) refreshToken: String?,
        request: HttpServletRequest
    ): ResponseEntity<String> {
        enforceRateLimit("refresh:${clientIp(request)}", 30, Duration.ofMinutes(5))
        return authenticationService.refreshToken(
            refreshToken
                ?.takeIf { it.isNotBlank() }
                ?: request.cookies?.firstOrNull { it.name == "refreshToken" }?.value
        )
    }

    @PostMapping("/password-recovery")
    @ResponseStatus(HttpStatus.OK)
    fun passwordRecovery(
        @RequestBody passwordRecoveryRequestDTO: PasswordRecoveryRequestDTO,
        request: HttpServletRequest
    ) {
        enforceRateLimit("pwd-recovery:${clientIp(request)}", 3, Duration.ofHours(1))
        authenticationService.passwordRecovery(passwordRecoveryRequestDTO.email)
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    fun resetPassword(
        @RequestBody resetPasswordDTO: ResetPasswordDTO,
        request: HttpServletRequest
    ) {
        enforceRateLimit("pwd-reset:${clientIp(request)}", 10, Duration.ofHours(1))
        authenticationService.resetPassword(resetPasswordDTO.token, resetPasswordDTO.newPassword)
    }

    private fun enforceRateLimit(key: String, limit: Int, period: Duration) {
        if (!rateLimitService.tryConsume(key, limit, period)) {
            throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Muitas tentativas. Tente novamente mais tarde.")
        }
    }

    private fun clientIp(request: HttpServletRequest): String {
        val header = request.getHeader("X-Forwarded-For")
        if (!header.isNullOrBlank()) return header.split(",")[0].trim()
        return request.remoteAddr ?: "unknown"
    }
}
