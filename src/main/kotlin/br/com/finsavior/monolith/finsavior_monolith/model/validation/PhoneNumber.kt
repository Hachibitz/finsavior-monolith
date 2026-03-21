package br.com.finsavior.monolith.finsavior_monolith.model.validation

import jakarta.validation.Constraint
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PhoneNumberValidator::class])
annotation class PhoneNumber(
    val message: String = "O número de telefone fornecido é inválido. Por favor, forneça um número de telefone com pelo menos DDD + 9 + 8 dígitos. Ex: 84999999999 ou +5584999999999",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)
