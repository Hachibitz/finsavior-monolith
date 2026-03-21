package br.com.finsavior.monolith.finsavior_monolith.model.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext

class PhoneNumberValidator : ConstraintValidator<PhoneNumber, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        if (value.isNullOrBlank()) {
            return false
        }

        val sanitizedPhone = value.replace(Regex("[^\\d+]"), "")
        val formattedPhone = if (sanitizedPhone.startsWith("+")) sanitizedPhone else "+$sanitizedPhone"

        val regex = Regex("^\\+[1-9]\\d{9,14}$")
        return regex.matches(formattedPhone)
    }
}
