package br.com.finsavior.monolith.finsavior_monolith.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.NOT_FOUND)
class UserNotFoundException(string: String) : RuntimeException()