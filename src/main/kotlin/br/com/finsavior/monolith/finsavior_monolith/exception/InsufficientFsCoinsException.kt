package br.com.finsavior.monolith.finsavior_monolith.exception

class InsufficientFsCoinsException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}