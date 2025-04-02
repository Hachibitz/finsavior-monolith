package br.com.finsavior.monolith.finsavior_monolith.exception

class PlanChangeException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}