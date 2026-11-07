package com.kaplay.app.features.rewards

import com.kaplay.app.core.auth.AuthRepository
import com.kaplay.app.core.auth.AuthState
import com.kaplay.app.core.ui.KAPlayToastController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal object AdRewardController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AdRewardState())
    val state: StateFlow<AdRewardState> = _state.asStateFlow()
    private var initialized = false
    private var lastInterstitialAtMs = 0L
    private val rewardedClaimGate = RewardClaimGate()
    private const val interstitialCooldownMs = 3L * 60L * 1_000L

    fun initialize() {
        if (initialized) return
        initialized = true
        RewardedAdsPlatform.initialize()
        scope.launch {
            AuthRepository.state.collect { auth ->
                if (auth is AuthState.Authenticated && !auth.isAnonymous) {
                    refresh()
                } else {
                    _state.value = AdRewardState()
                }
            }
        }
        RewardedAdsPlatform.load(
            onLoaded = { _state.value = _state.value.copy(error = null) },
            onFailed = { _state.value = _state.value.copy(error = "Anúncio recompensado indisponível no momento") },
        )
        RewardedAdsPlatform.loadInterstitial(onLoaded = {}, onFailed = {})
    }

    fun refresh() {
        _state.value = AdRewardRepository.currentState()
        scope.launch {
            _state.value = _state.value.copy(syncing = true)
            val result = AdRewardRepository.refresh()
            _state.value = AdRewardRepository.currentState().copy(
                syncing = false,
                error = result.exceptionOrNull()?.message,
            )
        }
    }

    fun updateRemainingTime() {
        val current = AdRewardRepository.currentState()
        _state.value = _state.value.copy(remainingMs = current.remainingMs)
    }

    fun showInterstitialThen(onContinue: () -> Unit) {
        val now = kotlin.time.Clock.System.now().toEpochMilliseconds()
        val rewardActive = AdRewardRepository.currentState().remainingMs > 0L
        if (rewardActive || now - lastInterstitialAtMs < interstitialCooldownMs) {
            onContinue()
            return
        }
        lastInterstitialAtMs = now
        RewardedAdsPlatform.showInterstitial(onDismissed = onContinue)
    }

    @OptIn(ExperimentalUuidApi::class)
    fun showRewarded() {
        val currentState = AdRewardRepository.currentState()
        if (currentState.remainingMs > 0L) {
            KAPlayToastController.show(activeAdRewardMessage(currentState.remainingMs))
            return
        }
        if (AdRewardRepository.remoteUserId() == null) {
            val message = "Entre na sua conta KA Play para ganhar 1 hora sem anúncios"
            _state.value = _state.value.copy(error = message)
            KAPlayToastController.show(message)
            return
        }
        if (!rewardedClaimGate.tryEnter()) return
        _state.value = _state.value.copy(syncing = true, error = null)
        val eventId = Uuid.random().toString()
        RewardedAdsPlatform.show(
            onCompleted = {
                scope.launch {
                    try {
                        val result = AdRewardRepository.claimAfterAd(eventId)
                        _state.value = AdRewardRepository.currentState().copy(
                            syncing = false,
                            error = result.exceptionOrNull()?.message,
                        )
                    } finally {
                        rewardedClaimGate.leave()
                    }
                }
            },
            onDismissed = {
                rewardedClaimGate.leave()
                _state.value = AdRewardRepository.currentState().copy(syncing = false)
            },
        )
    }
}
