package com.kaplay.app.features.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun signInFormExposesApprovedActionsAndForgotPassword() {
        var forgotPasswordClicks = 0
        setForm(
            isSignUp = false,
            onForgotPassword = { forgotPasswordClicks++ },
        )

        compose.onNodeWithText("Email").assertIsDisplayed()
        compose.onNodeWithText("Password").assertIsDisplayed()
        compose.onNodeWithText("Sign In").assertIsDisplayed()
        compose.onNodeWithText("Forgot password?").performClick()
        compose.onNodeWithText("Continue Without Account").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, forgotPasswordClicks) }
    }

    @Test
    fun signUpFormHidesForgotPassword() {
        setForm(isSignUp = true)

        compose.onNodeWithText("Create Account").assertIsDisplayed()
        compose.onNodeWithText("Forgot password?").assertDoesNotExist()
    }

    @Test
    fun passwordVisibilityActionIsFocusableAndInvokesCallback() {
        var visibilityClicks = 0
        setForm(
            isSignUp = false,
            onPasswordVisibilityToggle = { visibilityClicks++ },
        )

        compose.onNodeWithContentDescription("Show password").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, visibilityClicks) }
    }

    private fun setForm(
        isSignUp: Boolean,
        onForgotPassword: () -> Unit = {},
        onPasswordVisibilityToggle: () -> Unit = {},
    ) {
        compose.setContent {
            MaterialTheme {
                AuthForm(
                    isSignUp = isSignUp,
                    email = "",
                    password = "",
                    passwordVisible = false,
                    isLoading = false,
                    authError = null,
                    deviceLinkAuthState = com.kaplay.app.core.auth.DeviceLinkAuthState.Idle,
                    deviceLinkEnabled = false,
                    metrics = MobileAuthFormMetrics,
                    onEmailChange = {},
                    onPasswordChange = {},
                    onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                    onSubmit = {},
                    onToggleAuthMode = {},
                    onContinueWithoutAccount = {},
                    onStartDeviceLink = {},
                    onCancelDeviceLink = {},
                    onForgotPassword = onForgotPassword,
                    onEmailBoundsChange = { _: Rect -> },
                    onPasswordBoundsChange = { _: Rect -> },
                )
            }
        }
    }
}
