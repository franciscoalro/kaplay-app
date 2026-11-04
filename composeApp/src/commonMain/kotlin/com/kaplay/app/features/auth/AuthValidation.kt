package com.kaplay.app.features.auth

private val authEmailPattern = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

internal fun isValidAuthEmail(value: String): Boolean =
    normalizedAuthEmail(value) != null

internal fun normalizedAuthEmail(value: String): String? =
    value.trim().takeIf(authEmailPattern::matches)

internal fun canSubmitAuth(
    email: String,
    password: String,
    isLoading: Boolean,
): Boolean =
    !isLoading && isValidAuthEmail(email) && password.length >= 6
