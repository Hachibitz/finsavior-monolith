package br.com.finsavior.monolith.finsavior_monolith.controller.advice

import br.com.finsavior.monolith.finsavior_monolith.exception.AuthenticationException
import br.com.finsavior.monolith.finsavior_monolith.exception.ChatbotException
import br.com.finsavior.monolith.finsavior_monolith.exception.LoginException
import br.com.finsavior.monolith.finsavior_monolith.exception.UserNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(LoginException::class)
    fun handleLoginException(ex: LoginException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.message)
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFoundException(ex: UserNotFoundException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.message)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.message)
    }

    @ExceptionHandler(ChatbotException::class)
    fun handleChatbotException(ex: ChatbotException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.message)
    }
}