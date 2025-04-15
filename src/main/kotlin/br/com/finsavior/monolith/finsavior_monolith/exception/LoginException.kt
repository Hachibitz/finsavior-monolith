package br.com.finsavior.monolith.finsavior_monolith.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.BAD_REQUEST)
class LoginException : RuntimeException {
    constructor(message: String) : super(message)
}