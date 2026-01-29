package com.mgomanager.app.domain.util

object AccountNameValidator {
    const val MIN_NAME_LENGTH = 3
    const val MAX_NAME_LENGTH = 30
    private val namePattern = Regex("^[a-zA-Z0-9_-]+$")

    fun validate(name: String): String? {
        return when {
            name.isBlank() -> "name_blank"
            name.length < MIN_NAME_LENGTH -> "name_too_short"
            name.length > MAX_NAME_LENGTH -> "name_too_long"
            !namePattern.matches(name) -> "name_invalid_chars"
            else -> null
        }
    }
}
