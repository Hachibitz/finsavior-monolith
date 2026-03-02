package br.com.finsavior.monolith.finsavior_monolith.controller.advice

import br.com.finsavior.monolith.finsavior_monolith.exception.AiProcessDocumentException
import br.com.finsavior.monolith.finsavior_monolith.exception.AuthenticationException
import br.com.finsavior.monolith.finsavior_monolith.exception.CardDeletionException
import br.com.finsavior.monolith.finsavior_monolith.exception.CardNotFoundException
import br.com.finsavior.monolith.finsavior_monolith.exception.CardUnauthorizedException
import br.com.finsavior.monolith.finsavior_monolith.exception.ChatbotException
import br.com.finsavior.monolith.finsavior_monolith.exception.CommunicationException
import br.com.finsavior.monolith.finsavior_monolith.exception.InsufficientFsCoinsException
import br.com.finsavior.monolith.finsavior_monolith.exception.LoginException
import br.com.finsavior.monolith.finsavior_monolith.exception.UserNotFoundException
import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperApiException
import br.com.finsavior.monolith.finsavior_monolith.exception.WhisperLimitException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(CommunicationException::class)
    fun handleCommunicationException(ex: CommunicationException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "Communication error")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val msg = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return buildErrorResponse(HttpStatus.BAD_REQUEST, msg)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid argument")
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleRuntimeException(ex: RuntimeException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "Unexpected error")
    }

    @ExceptionHandler(LoginException::class)
    fun handleLoginException(ex: LoginException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthorized error")
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFoundException(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.message ?: "User not found")
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, ex.message ?: "Unauthenticated error")
    }

    @ExceptionHandler(ChatbotException::class)
    fun handleChatbotException(ex: ChatbotException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Processing chat error")
    }

    @ExceptionHandler(InsufficientFsCoinsException::class)
    fun handleInsufficientFsCoinsException(ex: InsufficientFsCoinsException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.PRECONDITION_FAILED, ex.message ?: "Insufficient parameters")
    }

    @ExceptionHandler
    fun handleWhisperApiException(ex: WhisperApiException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, ex.message ?: "Unexpected error")
    }

    @ExceptionHandler
    fun handleWhisperLimitException(ex: WhisperLimitException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.message ?: "Resource limit error")
    }

    @ExceptionHandler(AiProcessDocumentException::class)
    fun handleAiProcessDocumentException(ex: AiProcessDocumentException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ex.message ?: "Document AI processing/parsing failed")
    }

    @ExceptionHandler(CardNotFoundException::class)
    fun handleCardNotFoundException(ex: CardNotFoundException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.NOT_FOUND, ex.message ?: "Card not found")
    }

    @ExceptionHandler(CardUnauthorizedException::class)
    fun handleCardUnauthorizedException(ex: CardUnauthorizedException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.FORBIDDEN, ex.message ?: "Card access denied")
    }

    @ExceptionHandler(CardDeletionException::class)
    fun handleCardDeletionException(ex: CardDeletionException): ResponseEntity<ErrorResponse> {
        return buildErrorResponse(HttpStatus.CONFLICT, ex.message ?: "Card cannot be deleted due to linked resources")
    }

    private fun buildErrorResponse(status: HttpStatus, msg: String): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(status).body(ErrorResponse(status.ordinal, msg))
    }

    data class ErrorResponse(val code: Int, val msg: String)
}