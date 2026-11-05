package com.routinepulse.app.domain

/** Reasons a form field can be rejected. The UI maps each one to a localised message. */
enum class FieldError { EMPTY, INVALID_EMAIL, PASSWORD_TOO_SHORT, PASSWORD_WEAK, NAME_TOO_SHORT, TOO_LONG, CUSTOM_DAYS_REQUIRED }

/** Client-side validation. Rules mirror backend/src/validation.js so both sides agree. */
object Validators {
    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun email(value: String): FieldError? {
        val v = value.trim()
        return when {
            v.isEmpty() -> FieldError.EMPTY
            !EMAIL_REGEX.matches(v) -> FieldError.INVALID_EMAIL
            else -> null
        }
    }

    /** Login only checks the field is filled in - never reveal password rules to a login attempt. */
    fun loginPassword(value: String): FieldError? = if (value.isEmpty()) FieldError.EMPTY else null

    fun newPassword(value: String): FieldError? = when {
        value.isEmpty() -> FieldError.EMPTY
        value.length < 8 -> FieldError.PASSWORD_TOO_SHORT
        value.length > 72 -> FieldError.TOO_LONG
        !(value.any { it.isLetter() } && value.any { it.isDigit() }) -> FieldError.PASSWORD_WEAK
        else -> null
    }

    fun displayName(value: String): FieldError? {
        val v = value.trim()
        return when {
            v.isEmpty() -> FieldError.EMPTY
            v.length < 2 -> FieldError.NAME_TOO_SHORT
            v.length > 50 -> FieldError.TOO_LONG
            else -> null
        }
    }

    fun habitTitle(value: String): FieldError? {
        val v = value.trim()
        return when {
            v.isEmpty() -> FieldError.EMPTY
            v.length > 80 -> FieldError.TOO_LONG
            else -> null
        }
    }

    fun customDays(isCustom: Boolean, days: Set<Int>): FieldError? =
        if (isCustom && days.isEmpty()) FieldError.CUSTOM_DAYS_REQUIRED else null

    fun noteSubject(value: String): FieldError? = required(value, 60)
    fun noteTitle(value: String): FieldError? = required(value, 120)
    fun noteContent(value: String): FieldError? = required(value, 20000)

    private fun required(value: String, max: Int): FieldError? = when {
        value.trim().isEmpty() -> FieldError.EMPTY
        value.length > max -> FieldError.TOO_LONG
        else -> null
    }
}
