package com.kaplay.app.features.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.kaplay.app.core.ui.KAPlayLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaplay.app.core.auth.AuthRepository
import com.kaplay.app.core.auth.DeviceLinkAuthRepository
import com.kaplay.app.core.auth.DeviceLinkAuthState
import com.kaplay.app.core.ui.KAColors
import com.kaplay.app.core.ui.KAShapes
import com.kaplay.app.features.settings.AppBrandWordmark
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.compose_auth_already_have_account
import kaplay.composeapp.generated.resources.compose_auth_continue_without_account
import kaplay.composeapp.generated.resources.compose_auth_create_account
import kaplay.composeapp.generated.resources.compose_auth_dont_have_account
import kaplay.composeapp.generated.resources.compose_auth_email
import kaplay.composeapp.generated.resources.compose_auth_forgot_password
import kaplay.composeapp.generated.resources.compose_auth_hide_password
import kaplay.composeapp.generated.resources.compose_auth_password
import kaplay.composeapp.generated.resources.compose_auth_reset_failed
import kaplay.composeapp.generated.resources.compose_auth_reset_invalid_email
import kaplay.composeapp.generated.resources.compose_auth_reset_sent
import kaplay.composeapp.generated.resources.compose_auth_reset_sending
import kaplay.composeapp.generated.resources.compose_auth_show_password
import kaplay.composeapp.generated.resources.compose_auth_sign_in
import kaplay.composeapp.generated.resources.compose_auth_sign_in_subtitle
import kaplay.composeapp.generated.resources.compose_auth_sign_up
import kaplay.composeapp.generated.resources.compose_auth_sign_up_subtitle
import kaplay.composeapp.generated.resources.compose_auth_store_locally
import kaplay.composeapp.generated.resources.compose_auth_tagline
import kaplay.composeapp.generated.resources.compose_auth_terms_link
import kaplay.composeapp.generated.resources.compose_auth_terms_prefix
import kaplay.composeapp.generated.resources.compose_auth_welcome_back
import org.jetbrains.compose.resources.stringResource

private val AuthBackground = Color(0xFF090610)
private val AuthDeepPurple = Color(0xFF210B38)
internal val AuthAccent = Color(0xFFA844F5)
private val AuthAccentSoft = Color(0xFFC878FF)
internal val AuthTextPrimary = Color(0xFFF8F8FA)
internal val AuthTextSecondary = Color(0xFFD6CDE0)
private val AuthTextMuted = Color(0xFF9C8EAA)
private val AuthPrimaryButtonBackground = Color(0xFFF8F8FA)
private val AuthPrimaryButtonText = Color(0xFF101015)
internal val AuthFieldBackground = Color(0xFF171121)
private val AuthFieldBackgroundMobile = Color(0xFF171121)
internal val AuthFieldBorder = Color(0xFF493954)
private val AuthPaneBackground = Color(0xA6090610)
private val AuthPaneBorder = Color(0x80493954)
private val AuthSecondaryButtonBackground = Color(0x663A155C)
private val AuthSecondaryButtonBorder = Color(0xFFA844F5)

internal data class AuthFormMetrics(
    val fieldHeight: Dp,
    val fieldHorizontalPadding: Dp,
    val iconSize: Dp,
    val passwordIconSize: Dp,
    val primaryTop: Dp,
    val primaryHeight: Dp,
    val toggleTop: Dp,
    val dividerTop: Dp,
    val secondaryTop: Dp,
    val secondaryHeight: Dp,
    val fieldBackground: Color,
)

internal val MobileAuthFormMetrics = AuthFormMetrics(
    fieldHeight = 52.dp,
    fieldHorizontalPadding = 16.dp,
    iconSize = 20.dp,
    passwordIconSize = 20.dp,
    primaryTop = 20.dp,
    primaryHeight = 48.dp,
    toggleTop = 16.dp,
    dividerTop = 24.dp,
    secondaryTop = 20.dp,
    secondaryHeight = 48.dp,
    fieldBackground = KAColors.Surface,
)

private val LargeAuthFormMetrics = AuthFormMetrics(
    fieldHeight = 54.dp,
    fieldHorizontalPadding = 18.dp,
    iconSize = 20.dp,
    passwordIconSize = 20.dp,
    primaryTop = 24.dp,
    primaryHeight = 50.dp,
    toggleTop = 18.dp,
    dividerTop = 28.dp,
    secondaryTop = 24.dp,
    secondaryHeight = 50.dp,
    fieldBackground = KAColors.Surface,
)

private fun largeAuthScale(screenWidth: Dp): Float =
    (screenWidth.value / 1194f).coerceIn(1f, 1.32f)

private fun largeAuthFormMetrics(scale: Float): AuthFormMetrics = LargeAuthFormMetrics.copy(
    fieldHeight = 58.dp * scale,
    fieldHorizontalPadding = 18.dp * scale,
    iconSize = 20.dp * scale,
    passwordIconSize = 21.dp * scale,
    primaryTop = 24.dp * scale,
    primaryHeight = 56.dp * scale,
    toggleTop = 18.dp * scale,
    dividerTop = 30.dp * scale,
    secondaryTop = 26.dp * scale,
    secondaryHeight = 56.dp * scale,
)

@Composable
fun AuthScreen(
    modifier: Modifier = Modifier,
) {
    val authError by AuthRepository.error.collectAsStateWithLifecycle()
    val deviceLinkAuthState by DeviceLinkAuthRepository.state.collectAsStateWithLifecycle()
    val deviceLinkCompleting = (deviceLinkAuthState as? DeviceLinkAuthState.Waiting)?.isCompleting == true
    val serverConnectionState by ServerConnectionController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var isResetLoading by remember { mutableStateOf(false) }
    var resetMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var resetMessageIsError by rememberSaveable { mutableStateOf(false) }
    var emailFieldBounds by remember { mutableStateOf<Rect?>(null) }
    var passwordFieldBounds by remember { mutableStateOf<Rect?>(null) }
    val resetInvalidEmailText = stringResource(Res.string.compose_auth_reset_invalid_email)
    val resetSentText = stringResource(Res.string.compose_auth_reset_sent)
    val resetFailedText = stringResource(Res.string.compose_auth_reset_failed)

    fun submitAuth() {
        if (deviceLinkCompleting || !canSubmitAuth(email, password, isLoading || isResetLoading)) return
        val normalizedEmail = normalizedAuthEmail(email) ?: return
        resetMessage = null
        resetMessageIsError = false
        DeviceLinkAuthRepository.cancel()
        isLoading = true
        focusManager.clearFocus(force = true)
        scope.launch {
            if (isSignUp) AuthRepository.signUpWithEmail(normalizedEmail, password)
            else AuthRepository.signInWithEmail(normalizedEmail, password)
            isLoading = false
        }
    }

    fun requestPasswordReset() {
        if (isLoading || isResetLoading || deviceLinkCompleting) return
        AuthRepository.clearError()
        val resetEmail = normalizedAuthEmail(email)
        if (resetEmail == null) {
            resetMessage = resetInvalidEmailText
            resetMessageIsError = true
            return
        }
        DeviceLinkAuthRepository.cancel()
        isResetLoading = true
        resetMessage = null
        resetMessageIsError = false
        focusManager.clearFocus(force = true)
        scope.launch {
            try {
                val result = AuthRepository.requestPasswordReset(resetEmail)
                resetMessage = result.fold(
                    onSuccess = { resetSentText },
                    onFailure = { resetFailedText },
                )
                resetMessageIsError = result.isFailure
            } finally {
                isResetLoading = false
            }
        }
    }

    fun toggleAuthMode() {
        if (isResetLoading || deviceLinkCompleting) return
        DeviceLinkAuthRepository.cancel()
        isSignUp = !isSignUp
        resetMessage = null
        resetMessageIsError = false
        AuthRepository.clearError()
    }

    fun startDeviceLink() {
        if (isLoading || isResetLoading || deviceLinkCompleting) return
        resetMessage = null
        resetMessageIsError = false
        focusManager.clearFocus(force = true)
        AuthRepository.clearError()
        DeviceLinkAuthRepository.start()
    }

    fun continueWithoutAccount() {
        if (isLoading || isResetLoading || deviceLinkCompleting) return
        focusManager.clearFocus(force = true)
        DeviceLinkAuthRepository.cancel()
        AuthRepository.signInAnonymously()
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    DisposableEffect(serverConnectionState.activeServer) {
        onDispose(DeviceLinkAuthRepository::cancel)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AuthBackground)
            .pointerInput(emailFieldBounds, passwordFieldBounds) {
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val tappedTextField = listOfNotNull(emailFieldBounds, passwordFieldBounds)
                        .any { bounds -> bounds.contains(down.position) }
                    if (!tappedTextField) {
                        focusManager.clearFocus(force = true)
                    }
                }
            },
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
        ) {
            val screenWidth = maxWidth
            val largeScreen = screenWidth >= 900.dp
            val compactLargeScreen = screenWidth < 1100.dp

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .authGradientBackground(largeScreen = largeScreen),
            ) {
                if (largeScreen) {
                    val largeScale = largeAuthScale(screenWidth)
                    val formPaneWidth = (screenWidth * 0.30f).coerceIn(460.dp * largeScale, 720.dp * largeScale)
                    val brandHorizontalPadding = (screenWidth * 0.055f).coerceIn(56.dp * largeScale, 128.dp * largeScale)
                    val formHorizontalPadding = (formPaneWidth * 0.14f).coerceIn(48.dp, 96.dp)
                    val formMetrics = largeAuthFormMetrics(largeScale)

                    AuthLargeLayout(
                        modifier = Modifier.fillMaxSize(),
                        isSignUp = isSignUp,
                        email = email,
                        password = password,
                        passwordVisible = passwordVisible,
                        isLoading = isLoading,
                        authError = authError,
                        resetMessage = resetMessage,
                        resetMessageIsError = resetMessageIsError,
                        isResetLoading = isResetLoading,
                        deviceLinkAuthState = deviceLinkAuthState,
                        deviceLinkEnabled = serverConnectionState.activeServer.capabilities.tvLogin,
                        formPaneWidth = if (compactLargeScreen) 460.dp else formPaneWidth,
                        brandHorizontalPadding = brandHorizontalPadding,
                        formHorizontalPadding = formHorizontalPadding,
                        formMetrics = formMetrics,
                        scale = largeScale,
                        onEmailChange = {
                            email = it
                            resetMessage = null
                            resetMessageIsError = false
                            AuthRepository.clearError()
                        },
                        onPasswordChange = {
                            password = it
                            resetMessage = null
                            resetMessageIsError = false
                            AuthRepository.clearError()
                        },
                        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                        onSubmit = ::submitAuth,
                        onForgotPassword = ::requestPasswordReset,
                        onToggleAuthMode = ::toggleAuthMode,
                        onContinueWithoutAccount = ::continueWithoutAccount,
                        onStartDeviceLink = ::startDeviceLink,
                        onCancelDeviceLink = DeviceLinkAuthRepository::cancel,
                        onEmailBoundsChange = { emailFieldBounds = it },
                        onPasswordBoundsChange = { passwordFieldBounds = it },
                    )
                } else {
                    AuthMobileLayout(
                        isSignUp = isSignUp,
                        email = email,
                        password = password,
                        passwordVisible = passwordVisible,
                        isLoading = isLoading,
                        authError = authError,
                        resetMessage = resetMessage,
                        resetMessageIsError = resetMessageIsError,
                        isResetLoading = isResetLoading,
                        deviceLinkAuthState = deviceLinkAuthState,
                        deviceLinkEnabled = false,
                        statusBarTop = statusBarTop,
                        onEmailChange = {
                            email = it
                            resetMessage = null
                            resetMessageIsError = false
                            AuthRepository.clearError()
                        },
                        onPasswordChange = {
                            password = it
                            resetMessage = null
                            resetMessageIsError = false
                            AuthRepository.clearError()
                        },
                        onPasswordVisibilityToggle = { passwordVisible = !passwordVisible },
                        onSubmit = ::submitAuth,
                        onForgotPassword = ::requestPasswordReset,
                        onToggleAuthMode = ::toggleAuthMode,
                        onContinueWithoutAccount = ::continueWithoutAccount,
                        onStartDeviceLink = ::startDeviceLink,
                        onCancelDeviceLink = DeviceLinkAuthRepository::cancel,
                        onEmailBoundsChange = { emailFieldBounds = it },
                        onPasswordBoundsChange = { passwordFieldBounds = it },
                    )
                }
            }
        }

    }
}

@Composable
private fun AuthMobileLayout(
    isSignUp: Boolean,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    resetMessage: String?,
    resetMessageIsError: Boolean,
    isResetLoading: Boolean,
    deviceLinkAuthState: DeviceLinkAuthState,
    deviceLinkEnabled: Boolean,
    statusBarTop: Dp,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onStartDeviceLink: () -> Unit,
    onCancelDeviceLink: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                top = statusBarTop + 32.dp,
                end = 24.dp,
                bottom = 32.dp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AuthBrandLockup(logoHeight = 38.dp)

            Spacer(modifier = Modifier.height(48.dp))

            AuthHeading(
                isSignUp = isSignUp,
                headingStyle = MaterialTheme.typography.headlineLarge.copy(
                    color = AuthTextPrimary,
                    fontSize = 28.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                subtitleStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = AuthTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )

            Spacer(modifier = Modifier.height(28.dp))

            AuthForm(
                isSignUp = isSignUp,
                email = email,
                password = password,
                passwordVisible = passwordVisible,
                isLoading = isLoading,
                authError = authError,
                        resetMessage = resetMessage,
                        resetMessageIsError = resetMessageIsError,
                        isResetLoading = isResetLoading,
                deviceLinkAuthState = deviceLinkAuthState,
                deviceLinkEnabled = deviceLinkEnabled,
                metrics = MobileAuthFormMetrics,
                onEmailChange = onEmailChange,
                onPasswordChange = onPasswordChange,
                onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                onSubmit = onSubmit,
                 onForgotPassword = onForgotPassword,
                onToggleAuthMode = onToggleAuthMode,
                onContinueWithoutAccount = onContinueWithoutAccount,
                onStartDeviceLink = onStartDeviceLink,
                onCancelDeviceLink = onCancelDeviceLink,
                onEmailBoundsChange = onEmailBoundsChange,
                onPasswordBoundsChange = onPasswordBoundsChange,
            )
        }
    }
}

@Composable
private fun AuthLargeLayout(
    modifier: Modifier = Modifier,
    isSignUp: Boolean,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    resetMessage: String?,
    resetMessageIsError: Boolean,
    isResetLoading: Boolean,
    deviceLinkAuthState: DeviceLinkAuthState,
    deviceLinkEnabled: Boolean,
    formPaneWidth: Dp,
    brandHorizontalPadding: Dp,
    formHorizontalPadding: Dp,
    formMetrics: AuthFormMetrics,
    scale: Float,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onStartDeviceLink: () -> Unit,
    onCancelDeviceLink: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    Row(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = brandHorizontalPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            AppBrandWordmark(
                contentDescription = null,
                modifier = Modifier.height(60.dp * scale),
            )
            Spacer(modifier = Modifier.height(32.dp * scale))
            Text(
                text = stringResource(Res.string.compose_auth_tagline),
                modifier = Modifier.widthIn(max = 440.dp * scale),
                style = MaterialTheme.typography.displayLarge.copy(
                    color = AuthTextPrimary,
                    fontSize = (40f * scale).sp,
                    lineHeight = (45f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Spacer(modifier = Modifier.height(18.dp * scale))
            Text(
                text = stringResource(Res.string.compose_auth_sign_up_subtitle),
                modifier = Modifier.widthIn(max = 400.dp * scale),
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = AuthTextSecondary,
                    fontSize = (17f * scale).sp,
                    lineHeight = (26f * scale).sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }

        Box(
            modifier = Modifier
                .width(formPaneWidth)
                .fillMaxHeight()
                .background(AuthPaneBackground)
                .drawBehind {
                    drawLine(
                        color = AuthPaneBorder,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = formHorizontalPadding, vertical = 32.dp * scale),
                verticalArrangement = Arrangement.Center,
            ) {
                AuthHeading(
                    isSignUp = isSignUp,
                    headingStyle = MaterialTheme.typography.headlineLarge.copy(
                        color = AuthTextPrimary,
                        fontSize = (30f * scale).sp,
                        lineHeight = (33f * scale).sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    subtitleStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = AuthTextSecondary,
                        fontSize = (15f * scale).sp,
                        lineHeight = (21f * scale).sp,
                        fontWeight = FontWeight.Normal,
                    ),
                )

                Spacer(modifier = Modifier.height(32.dp * scale))

                AuthForm(
                    isSignUp = isSignUp,
                    email = email,
                    password = password,
                    passwordVisible = passwordVisible,
                    isLoading = isLoading,
                    authError = authError,
                    resetMessage = resetMessage,
                    resetMessageIsError = resetMessageIsError,
                    isResetLoading = isResetLoading,
                    deviceLinkAuthState = deviceLinkAuthState,
                    deviceLinkEnabled = deviceLinkEnabled,
                    metrics = formMetrics,
                    scale = scale,
                    onEmailChange = onEmailChange,
                    onPasswordChange = onPasswordChange,
                    onPasswordVisibilityToggle = onPasswordVisibilityToggle,
                    onSubmit = onSubmit,
                    onForgotPassword = onForgotPassword,
                    onToggleAuthMode = onToggleAuthMode,
                    onContinueWithoutAccount = onContinueWithoutAccount,
                    onStartDeviceLink = onStartDeviceLink,
                    onCancelDeviceLink = onCancelDeviceLink,
                    onEmailBoundsChange = onEmailBoundsChange,
                    onPasswordBoundsChange = onPasswordBoundsChange,
                )
            }
        }
    }
}

@Composable
private fun AuthBrandLockup(
    logoHeight: Dp,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppBrandWordmark(
            contentDescription = null,
            modifier = Modifier.height(logoHeight),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(Res.string.compose_auth_tagline),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun AuthHeading(
    isSignUp: Boolean,
    headingStyle: TextStyle,
    subtitleStyle: TextStyle,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authHeading",
        ) { signUp ->
            Text(
                text = if (signUp) stringResource(Res.string.compose_auth_create_account)
                else stringResource(Res.string.compose_auth_welcome_back),
                style = headingStyle,
                color = AuthTextPrimary,
            )
        }
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authSubtitle",
        ) { signUp ->
            Text(
                text = if (signUp) stringResource(Res.string.compose_auth_sign_up_subtitle)
                else stringResource(Res.string.compose_auth_sign_in_subtitle),
                style = subtitleStyle,
                color = AuthTextSecondary,
            )
        }
    }
}

@Composable
internal fun AuthForm(
    isSignUp: Boolean,
    email: String,
    password: String,
    passwordVisible: Boolean,
    isLoading: Boolean,
    authError: String?,
    resetMessage: String?,
    resetMessageIsError: Boolean,
    isResetLoading: Boolean,
    deviceLinkAuthState: DeviceLinkAuthState,
    deviceLinkEnabled: Boolean,
    metrics: AuthFormMetrics,
    scale: Float = 1f,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordVisibilityToggle: () -> Unit,
    onSubmit: () -> Unit,
    onForgotPassword: () -> Unit,
    onToggleAuthMode: () -> Unit,
    onContinueWithoutAccount: () -> Unit,
    onStartDeviceLink: () -> Unit,
    onCancelDeviceLink: () -> Unit,
    onEmailBoundsChange: (Rect) -> Unit,
    onPasswordBoundsChange: (Rect) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    var forgotPasswordFocused by remember { mutableStateOf(false) }
    var resetWasLoading by remember { mutableStateOf(false) }
    val forgotPasswordFocusRequester = remember { FocusRequester() }
    val deviceLinkCompleting = (deviceLinkAuthState as? DeviceLinkAuthState.Waiting)?.isCompleting == true
    val authActionsEnabled = !isLoading && !isResetLoading && !deviceLinkCompleting

    LaunchedEffect(isResetLoading, authActionsEnabled, isSignUp) {
        if (isResetLoading) {
            resetWasLoading = true
        } else if (resetWasLoading && !isSignUp && authActionsEnabled) {
            forgotPasswordFocusRequester.requestFocus()
            resetWasLoading = false
        }
    }
    val submitEnabled = canSubmitAuth(email, password, isLoading || isResetLoading || deviceLinkCompleting)
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        AuthTextField(
            value = email,
            onValueChange = onEmailChange,
            placeholder = stringResource(Res.string.compose_auth_email),
            icon = Icons.Rounded.Email,
            metrics = metrics,
            scale = scale,
            enabled = authActionsEnabled,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                onEmailBoundsChange(coordinates.boundsInRoot())
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )

        Spacer(modifier = Modifier.height(14.dp))

        AuthTextField(
            value = password,
            onValueChange = onPasswordChange,
            placeholder = stringResource(Res.string.compose_auth_password),
            icon = Icons.Rounded.Lock,
            metrics = metrics,
            scale = scale,
            enabled = authActionsEnabled,
            isPassword = true,
            passwordVisible = passwordVisible,
            onPasswordVisibilityToggle = onPasswordVisibilityToggle,
            modifier = Modifier.onGloballyPositioned { coordinates ->
                onPasswordBoundsChange(coordinates.boundsInRoot())
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onSubmit() },
            ),
        )

        if (!isSignUp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp * scale),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isResetLoading) {
                    KAPlayLoadingIndicator(
                        modifier = Modifier.size(14.dp * scale),
                        color = AuthAccentSoft,
                    )
                    Spacer(modifier = Modifier.width(8.dp * scale))
                    Text(
                        text = stringResource(Res.string.compose_auth_reset_sending),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = AuthAccentSoft,
                            fontSize = (13f * scale).sp,
                            lineHeight = (18f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                } else {
                    Text(
                        text = stringResource(Res.string.compose_auth_forgot_password),
                        modifier = Modifier
                            .focusRequester(forgotPasswordFocusRequester)
                            .onFocusChanged { forgotPasswordFocused = it.isFocused }
                            .clickable(
                                enabled = authActionsEnabled,
                                onClick = onForgotPassword,
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (forgotPasswordFocused) KAColors.TextPrimary else KAColors.PrimaryLight,
                            fontSize = (13f * scale).sp,
                            lineHeight = (18f * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }

        authError?.let { errorText ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.error,
                    fontSize = (13f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ),
            )
        }

        resetMessage?.let { message ->
            Spacer(modifier = Modifier.height(8.dp * scale))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (resetMessageIsError) MaterialTheme.colorScheme.error else AuthAccentSoft,
                    fontSize = (13f * scale).sp,
                    lineHeight = (18f * scale).sp,
                ),
            )
        }

        if (isSignUp) {
            Spacer(modifier = Modifier.height(14.dp * scale))
            AuthTermsAcknowledgement(
                enabled = authActionsEnabled,
                scale = scale,
                onTermsClick = { uriHandler.openUri("https://kaplay.tv/terms") },
            )
        }

        Spacer(modifier = Modifier.height(metrics.primaryTop))

        AuthPrimaryButton(
            text = if (isSignUp) stringResource(Res.string.compose_auth_create_account)
            else stringResource(Res.string.compose_auth_sign_in),
            isLoading = isLoading,
            enabled = submitEnabled,
            height = metrics.primaryHeight,
            scale = scale,
            onClick = onSubmit,
        )

        Spacer(modifier = Modifier.height(metrics.toggleTop))

        AuthModeToggle(
            isSignUp = isSignUp,
            enabled = authActionsEnabled,
            scale = scale,
            onToggleAuthMode = onToggleAuthMode,
        )

        Spacer(modifier = Modifier.height(metrics.secondaryTop))

        AuthSecondaryButton(
            text = stringResource(Res.string.compose_auth_continue_without_account),
            enabled = authActionsEnabled,
            height = metrics.secondaryHeight,
            scale = scale,
            onClick = onContinueWithoutAccount,
        )

        if (deviceLinkEnabled) {
            Spacer(modifier = Modifier.height(12.dp * scale))
            DeviceLinkAuthSection(
                state = deviceLinkAuthState,
                enabled = authActionsEnabled,
                height = metrics.secondaryHeight,
                scale = scale,
                onStart = onStartDeviceLink,
                onCancel = onCancelDeviceLink,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = stringResource(Res.string.compose_auth_store_locally),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextMuted,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AuthTermsAcknowledgement(
    enabled: Boolean,
    scale: Float,
    onTermsClick: () -> Unit,
) {
    var termsFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.compose_auth_terms_prefix),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextSecondary,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
        )
        Spacer(modifier = Modifier.width(4.dp * scale))
        Text(
            text = stringResource(Res.string.compose_auth_terms_link),
            modifier = Modifier
                .onFocusChanged { termsFocused = it.isFocused }
                .clip(RoundedCornerShape(8.dp))
                .border(
                    width = if (termsFocused) 2.dp else 0.dp,
                    color = AuthAccent,
                    shape = RoundedCornerShape(8.dp),
                )
                .clickable(enabled = enabled, onClick = onTermsClick)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = AuthTextPrimary,
                fontSize = (13f * scale).sp,
                lineHeight = (18f * scale).sp,
                fontWeight = FontWeight.SemiBold,
            ),
        )
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    metrics: AuthFormMetrics,
    scale: Float,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordVisibilityToggle: () -> Unit = {},
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val shape = KAShapes.Field
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.fieldHeight)
            .background(metrics.fieldBackground, shape)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) AuthAccent else AuthFieldBorder,
                shape = shape,
            )
            .padding(horizontal = metrics.fieldHorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(metrics.iconSize),
            tint = AuthTextMuted,
        )
        Spacer(modifier = Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onFocusChanged { isFocused = it.hasFocus },
            singleLine = true,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = AuthTextPrimary,
                fontSize = (16f * scale).sp,
                lineHeight = (22f * scale).sp,
                fontWeight = FontWeight.Normal,
            ),
            cursorBrush = SolidColor(AuthTextPrimary),
            visualTransformation = if (isPassword && !passwordVisible) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = AuthTextMuted,
                                fontSize = (16f * scale).sp,
                                lineHeight = (22f * scale).sp,
                                fontWeight = FontWeight.Normal,
                            ),
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (isPassword) {
            Spacer(modifier = Modifier.width(12.dp))
            var visibilityFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .onFocusChanged { visibilityFocused = it.isFocused }
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = if (visibilityFocused) 2.dp else 0.dp,
                        color = AuthAccent,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = enabled, onClick = onPasswordVisibilityToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff
                    else Icons.Rounded.Visibility,
                    contentDescription = stringResource(
                        if (passwordVisible) Res.string.compose_auth_hide_password
                        else Res.string.compose_auth_show_password,
                    ),
                    modifier = Modifier.size(metrics.passwordIconSize),
                    tint = AuthTextSecondary,
                )
            }
        }
    }
}

@Composable
internal fun AuthPrimaryButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled,
        shape = KAShapes.Button,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = AuthPrimaryButtonBackground,
            contentColor = AuthPrimaryButtonText,
            disabledContainerColor = if (isLoading) {
                AuthPrimaryButtonBackground
            } else {
                AuthPrimaryButtonBackground.copy(alpha = 0.45f)
            },
            disabledContentColor = AuthPrimaryButtonText.copy(alpha = 0.55f),
        ),
    ) {
        if (isLoading) {
            KAPlayLoadingIndicator(
                modifier = Modifier.size(20.dp * scale),
                color = AuthPrimaryButtonText,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = (16f * scale).sp,
                    lineHeight = (22f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun AuthModeToggle(
    isSignUp: Boolean,
    enabled: Boolean,
    scale: Float,
    onToggleAuthMode: () -> Unit,
) {
    var actionFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authTogglePrompt",
        ) { signUp ->
            Text(
                text = if (signUp) {
                    stringResource(Res.string.compose_auth_already_have_account).trimEnd()
                } else {
                    stringResource(Res.string.compose_auth_dont_have_account).trimEnd()
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = AuthTextSecondary,
                    fontSize = (14f * scale).sp,
                    lineHeight = (20f * scale).sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        AnimatedContent(
            targetState = isSignUp,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "authToggleAction",
        ) { signUp ->
            Text(
                text = if (signUp) stringResource(Res.string.compose_auth_sign_in)
                else stringResource(Res.string.compose_auth_sign_up),
                modifier = Modifier
                    .onFocusChanged { actionFocused = it.isFocused }
                    .clickable(enabled = enabled, onClick = onToggleAuthMode)
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (actionFocused) KAColors.TextPrimary else KAColors.PrimaryLight,
                    fontSize = (14f * scale).sp,
                    lineHeight = (20f * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
internal fun AuthSecondaryButton(
    text: String,
    enabled: Boolean,
    height: Dp,
    scale: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        enabled = enabled,
        shape = KAShapes.Button,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = KAColors.SurfaceVariant,
            contentColor = Color.White,
            disabledContainerColor = KAColors.SurfaceVariant.copy(alpha = 0.45f),
            disabledContentColor = Color.White.copy(alpha = 0.55f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = (15f * scale).sp,
                lineHeight = (21f * scale).sp,
                fontWeight = FontWeight.Medium,
            ),
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.authGradientBackground(largeScreen: Boolean): Modifier = drawWithCache {
    val angleDegrees = if (largeScreen) 122.0 else 148.0
    val angleRadians = angleDegrees * PI / 180.0
    val directionX = sin(angleRadians).toFloat()
    val directionY = (-cos(angleRadians)).toFloat()
    val halfLength = (abs(size.width * directionX) + abs(size.height * directionY)) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    val start = Offset(
        x = center.x - directionX * halfLength,
        y = center.y - directionY * halfLength,
    )
    val end = Offset(
        x = center.x + directionX * halfLength,
        y = center.y + directionY * halfLength,
    )
    val colorStops = if (largeScreen) {
        arrayOf(
            0f to AuthAccent.copy(alpha = 0.18f),
            0.14f to AuthDeepPurple,
            0.30f to Color(0xFF32104D),
            0.46f to Color(0xFF170B25),
            0.64f to AuthBackground,
            0.82f to AuthBackground,
            1f to AuthBackground,
        )
    } else {
        arrayOf(
            0f to AuthAccent.copy(alpha = 0.14f),
            0.12f to AuthDeepPurple,
            0.27f to Color(0xFF32104D),
            0.42f to Color(0xFF170B25),
            0.60f to AuthBackground,
            0.80f to AuthBackground,
            1f to AuthBackground,
        )
    }
    val brush = Brush.linearGradient(
        colorStops = colorStops,
        start = start,
        end = end,
    )
    onDrawBehind {
        drawRect(brush = brush)
    }
}

private fun LayoutCoordinates.boundsInRoot(): Rect {
    val position = positionInRoot()
    return Rect(
        left = position.x,
        top = position.y,
        right = position.x + size.width,
        bottom = position.y + size.height,
    )
}
