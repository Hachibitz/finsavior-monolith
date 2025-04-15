package br.com.finsavior.monolith.finsavior_monolith.controller.advice

import br.com.finsavior.monolith.finsavior_monolith.exception.LoginException
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
}