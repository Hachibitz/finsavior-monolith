package br.com.finsavior.monolith.finsavior_monolith.controller

import br.com.finsavior.monolith.finsavior_monolith.model.dto.LoginRequestDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.ResetPasswordDTO
import br.com.finsavior.monolith.finsavior_monolith.model.dto.SignUpDTO
import br.com.finsavior.monolith.finsavior_monolith.service.AuthenticationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/auth")
class AuthController(private val authenticationService: AuthenticationService) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@RequestBody signUpDTO: SignUpDTO) =
        authenticationService.signUp(signUpDTO)

    @PostMapping("/login-auth")
    fun login(
        @RequestBody loginRequest: LoginRequestDTO,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<Map<String, String>> {
        return authenticationService.login(loginRequest, request, response)
    }

    @PostMapping("/login-google")
    fun loginWithGoogle(
        @RequestBody idTokenString: String,
        request: HttpServletRequest,
        response: HttpServletResponse
    ): ResponseEntity<MutableMap<String, String>> {
        return authenticationService.loginWithGoogle(idTokenString, request, response)
    }

    @GetMapping("/validate-token")
    fun validateToken(@RequestParam token: String?): ResponseEntity<Boolean?> {
        return authenticationService.validateToken(token)
    }

    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody refreshToken: String): ResponseEntity<String> =
        authenticationService.refreshToken(refreshToken)

    @PostMapping("/password-recovery")
    @ResponseStatus(HttpStatus.OK)
    fun passwordRecovery(@RequestParam email: String) =
        authenticationService.passwordRecovery(email)

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.OK)
    fun resetPassword(@RequestBody resetPasswordDTO: ResetPasswordDTO) =
        authenticationService.resetPassword(resetPasswordDTO.token, resetPasswordDTO.newPasswordDTO)
}