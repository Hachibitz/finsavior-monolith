package br.com.finsavior.monolith.finsavior_monolith.util

import java.util.regex.Pattern

object PasswordValidator {
    private val UPPER_CASE_PATTERN: Pattern = Pattern.compile("[A-Z]")
    private val LOWER_CASE_PATTERN: Pattern = Pattern.compile("[a-z]")
    private val DIGIT_PATTERN: Pattern = Pattern.compile("\\d")
    private val SPECIAL_CHAR_PATTERN: Pattern = Pattern.compile("[\\W_]")
    private const val MIN_LENGTH = 8

    fun isValid(password: String?): Boolean {
        if (password == null) {
            return false
        }

        val hasUpperCase = UPPER_CASE_PATTERN.matcher(password).find()
        val hasLowerCase = LOWER_CASE_PATTERN.matcher(password).find()
        val hasDigit = DIGIT_PATTERN.matcher(password).find()
        val hasSpecialChar = SPECIAL_CHAR_PATTERN.matcher(password).find()
        val hasMinLength = password.length >= MIN_LENGTH

        return hasUpperCase && hasLowerCase && hasDigit && hasSpecialChar && hasMinLength
    }
}
