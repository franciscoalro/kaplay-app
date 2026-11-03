package com.kaplay.app.features.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthValidationTest {
    @Test
    fun blankEmailIsInvalid() {
        assertFalse(isValidAuthEmail("   "))
    }

    @Test
    fun malformedEmailIsInvalid() {
        assertFalse(isValidAuthEmail("user@"))
        assertFalse(isValidAuthEmail("user.example.com"))
    }

    @Test
    fun trimmedEmailWithDomainIsValid() {
        assertTrue(isValidAuthEmail("  user@example.com  "))
    }

    @Test
    fun shortPasswordCannotSubmit() {
        assertFalse(canSubmitAuth("user@example.com", "12345", isLoading = false))
    }

    @Test
    fun loadingStateCannotSubmit() {
        assertFalse(canSubmitAuth("user@example.com", "123456", isLoading = true))
    }

    @Test
    fun validCredentialsCanSubmit() {
        assertTrue(canSubmitAuth("user@example.com", "123456", isLoading = false))
    }
}
