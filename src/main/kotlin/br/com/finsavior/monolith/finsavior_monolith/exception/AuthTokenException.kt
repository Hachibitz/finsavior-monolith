package br.com.finsavior.monolith.finsavior_monolith.exception

import org.springframework.http.HttpStatus

class AuthTokenException(message: String, status: HttpStatus) : RuntimeException(message)