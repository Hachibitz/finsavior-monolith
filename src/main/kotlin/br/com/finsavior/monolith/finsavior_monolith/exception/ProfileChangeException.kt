package br.com.finsavior.monolith.finsavior_monolith.exception

import org.springframework.http.HttpStatus

class ProfileChangeException(string: String, badRequest: HttpStatus) : RuntimeException() {
}