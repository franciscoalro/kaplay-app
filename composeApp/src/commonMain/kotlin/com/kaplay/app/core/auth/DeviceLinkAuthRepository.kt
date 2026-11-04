package com.kaplay.app.core.auth

import co.touchlab.kermit.Logger
import com.kaplay.app.core.network.ServerConfiguration
import com.kaplay.app.core.network.ServerConfigurationRepository
import com.kaplay.app.core.network.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.statement.bodyAsText
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface DeviceLinkAuthState {
    data object Idle : DeviceLinkAuthState
    data object Starting : DeviceLinkAuthState
    data class Waiting(
        val code: String,
        val verificationUrl: String,
        val isCompleting: Boolean = false,
    ) : DeviceLinkAuthState
    data class Failed(val reason: DeviceLinkAuthFailure) : DeviceLinkAuthState
}

enum class DeviceLinkAuthFailure {
    Start,
    Expired,
    Complete,
}

object DeviceLinkAuthRepository {
    private const val maxConsecutivePollFailures = 3
    private const val maxPollAttempts = 120
    private const val officialLinkUrl = "https://kaplay.tv/link"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("DeviceLinkAuthRepository")
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<DeviceLinkAuthState>(DeviceLinkAuthState.Idle)
    val state: StateFlow<DeviceLinkAuthState> = _state.asStateFlow()
    private val stateLock = SynchronizedObject()
    private var activeGeneration = 0L
    private var activeJob: Job? = null

    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        synchronized(stateLock) {
            if (_state.value is DeviceLinkAuthState.Starting || _state.value is DeviceLinkAuthState.Waiting) return

            activeGeneration += 1
            activeJob?.cancel()
            val generation = activeGeneration
            activeJob = scope.launch {
                if (!setStateIfCurrent(generation, DeviceLinkAuthState.Starting)) return@launch
                val configuration = ServerConfigurationRepository.active.value
                val nonce = Uuid.random().toString()
                try {
                    val session = startSession(
                        configuration = configuration,
                        nonce = nonce,
                        deviceName = currentDeviceClientMetadata().deviceName,
                    )
                    if (!setStateIfCurrent(
                            generation,
                            DeviceLinkAuthState.Waiting(
                                code = formatDeviceLinkCode(session.userCode),
                                verificationUrl = session.verificationUriComplete,
                            ),
                        )
                    ) return@launch
                    pollAndComplete(session, nonce, generation)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: DeviceLinkAuthException) {
                    log.w(error) { "Device link sign-in stopped" }
                    setStateIfCurrent(generation, DeviceLinkAuthState.Failed(error.reason))
                } catch (error: Throwable) {
                    log.w(error) { "Device link sign-in failed" }
                    setStateIfCurrent(generation, DeviceLinkAuthState.Failed(DeviceLinkAuthFailure.Start))
                }
            }
        }
    }

    fun cancel() {
        val job = synchronized(stateLock) {
            activeGeneration += 1
            val previousJob = activeJob
            activeJob = null
            _state.value = DeviceLinkAuthState.Idle
            previousJob
        }
        job?.cancel()
    }

    private suspend fun startSession(
        configuration: ServerConfiguration,
        nonce: String,
        deviceName: String,
    ): DeviceLinkStartResponse {
        val params = buildJsonObject {
            put("p_device_nonce", nonce)
            put("p_redirect_base_url", configuration.deviceLinkUrl())
            put("p_device_name", deviceName)
            put("p_device_type", "mobile")
        }
        return SupabaseProvider.client.postgrest
            .rpc("start_device_login_session", params)
            .decodeList<DeviceLinkStartResponse>()
            .firstOrNull()
            ?.takeIf {
                it.deviceCode.isNotBlank() &&
                    it.userCode.isNotBlank() &&
                    it.verificationUriComplete.isNotBlank()
            }
            ?: throw DeviceLinkAuthException(DeviceLinkAuthFailure.Start)
    }

    private suspend fun pollAndComplete(
        session: DeviceLinkStartResponse,
        nonce: String,
        generation: Long,
    ) {
        var pollAttempts = 0
        var consecutiveFailures = 0
        val intervalMillis = session.pollIntervalSeconds.coerceIn(2, 10) * 1_000L

        while (currentCoroutineContext().isActive && pollAttempts < maxPollAttempts) {
            delay(intervalMillis)
            pollAttempts += 1
            val poll = try {
                val params = buildJsonObject {
                    put("p_code", session.deviceCode)
                    put("p_device_nonce", nonce)
                }
                SupabaseProvider.client.postgrest
                    .rpc("poll_tv_login_session", params)
                    .decodeList<DeviceLinkPollResponse>()
                    .firstOrNull()
                    ?: throw DeviceLinkAuthException(DeviceLinkAuthFailure.Complete)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                consecutiveFailures += 1
                if (consecutiveFailures >= maxConsecutivePollFailures) throw error
                continue
            }
            consecutiveFailures = 0

            when (poll.status.trim().lowercase()) {
                "pending" -> Unit
                "approved" -> {
                    val shouldComplete = synchronized(stateLock) {
                        if (activeGeneration != generation) {
                            false
                        } else {
                            _state.value = DeviceLinkAuthState.Waiting(
                                code = formatDeviceLinkCode(session.userCode),
                                verificationUrl = session.verificationUriComplete,
                                isCompleting = true,
                            )
                            true
                        }
                    }
                    if (!shouldComplete) return
                    completeSession(session.deviceCode, nonce, generation)
                    return
                }
                else -> throw DeviceLinkAuthException(DeviceLinkAuthFailure.Expired)
            }
        }

        throw DeviceLinkAuthException(DeviceLinkAuthFailure.Expired)
    }

    private suspend fun completeSession(deviceCode: String, nonce: String, generation: Long) {
        var importAttempted = false
        var importedAccessToken: String? = null
        var importedRefreshToken: String? = null
        try {
            ensureGenerationActive(generation)
            val payload = buildJsonObject {
                put("code", deviceCode)
                put("device_nonce", nonce)
            }
            val response = SupabaseProvider.client.functions.invoke("tv-logins-exchange", payload)
            val result = json.decodeFromString<DeviceLinkExchangeResponse>(response.bodyAsText())
            val accessToken = result.accessToken.takeIf { it.isNotBlank() }
                ?: throw DeviceLinkAuthException(DeviceLinkAuthFailure.Complete)
            val refreshToken = result.refreshToken.takeIf { it.isNotBlank() }
                ?: throw DeviceLinkAuthException(DeviceLinkAuthFailure.Complete)
            val expiresIn = requireNotNull(result.expiresIn?.takeIf { it > 0L })
            val user = SupabaseProvider.client.auth.retrieveUser(accessToken)
            if (user.id.orEmpty().isBlank()) {
                throw DeviceLinkAuthException(DeviceLinkAuthFailure.Complete)
            }
            importedAccessToken = accessToken
            importedRefreshToken = refreshToken
            importAttempted = true
            AuthRepository.withSessionMutation {
                try {
                    withContext(NonCancellable) {
                        SupabaseProvider.client.auth.importSession(
                            UserSession(
                                accessToken = accessToken,
                                refreshToken = refreshToken,
                                expiresIn = expiresIn,
                                tokenType = result.tokenType?.takeIf { it.isNotBlank() } ?: "bearer",
                                user = user,
                            ),
                        )
                    }
                } catch (error: Throwable) {
                    withContext(NonCancellable) {
                        runCatching { SupabaseProvider.client.auth.clearSession() }
                    }
                    throw error
                }
                ensureGenerationActive(generation)
                synchronized(stateLock) {
                    if (activeGeneration != generation) {
                        throw CancellationException("Device link sign-in was cancelled")
                    }
                    if (!AuthRepository.onRemoteSessionImported(user.id, user.email)) {
                        throw DeviceLinkAuthException(DeviceLinkAuthFailure.Complete)
                    }
                    _state.value = DeviceLinkAuthState.Idle
                }
            }
        } catch (error: CancellationException) {
            if (importAttempted) {
                clearImportedSessionIfOwned(importedAccessToken, importedRefreshToken)
            }
            throw error
        } catch (error: Throwable) {
            if (importAttempted) {
                clearImportedSessionIfOwned(importedAccessToken, importedRefreshToken)
            }
            throw DeviceLinkAuthException(DeviceLinkAuthFailure.Complete, error)
        }
    }

    private suspend fun clearImportedSessionIfOwned(accessToken: String?, refreshToken: String?) {
        runCatching {
            AuthRepository.clearRemoteSessionIfMatches(accessToken, refreshToken)
        }.onFailure { error ->
            log.w(error) { "Unable to clear the cancelled device-link session" }
        }
    }

    private suspend fun ensureGenerationActive(generation: Long) {
        currentCoroutineContext().ensureActive()
        if (!isCurrentGeneration(generation)) {
            throw CancellationException("Device link sign-in was cancelled")
        }
    }

    private fun isCurrentGeneration(generation: Long): Boolean =
        synchronized(stateLock) { activeGeneration == generation }

    private fun setStateIfCurrent(generation: Long, state: DeviceLinkAuthState): Boolean =
        synchronized(stateLock) {
            if (activeGeneration != generation) {
                false
            } else {
                _state.value = state
                true
            }
        }

    private fun ServerConfiguration.deviceLinkUrl(): String =
        if (isCustom) "${backendUrl.trimEnd('/')}/link" else officialLinkUrl
}

internal fun formatDeviceLinkCode(value: String): String {
    val normalized = value.uppercase().filter { it.isLetterOrDigit() }.take(6)
    return if (normalized.length <= 3) normalized else "${normalized.take(3)}-${normalized.drop(3)}"
}

@Serializable
private data class DeviceLinkStartResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String,
    @SerialName("poll_interval_seconds") val pollIntervalSeconds: Int = 3,
)

@Serializable
private data class DeviceLinkPollResponse(
    val status: String,
)

@Serializable
private data class DeviceLinkExchangeResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val user: UserInfo? = null,
)

private class DeviceLinkAuthException(
    val reason: DeviceLinkAuthFailure,
    cause: Throwable? = null,
) : Exception(reason.name, cause)
