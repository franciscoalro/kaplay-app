package com.kaplay.app.features.rewards

import com.kaplay.app.core.auth.AuthRepository
import com.kaplay.app.core.auth.AuthState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object AdRewardController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(AdRewardState())
    val state: StateFlow<AdRewardState> = _state.asStateFlow()
    private var initialized = false

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

    fun showRewarded() {
        if (AdRewardRepository.remoteUserId() == null) {
            _state.value = _state.value.copy(error = "Entre na sua conta KA Play para ganhar 1 hora sem anúncios")
            return
        }
        _state.value = _state.value.copy(syncing = true, error = null)
        RewardedAdsPlatform.show(
            onCompleted = {
                scope.launch {
                    val result = AdRewardRepository.claimAfterAd()
                    _state.value = AdRewardRepository.currentState().copy(
                        syncing = false,
                        error = result.exceptionOrNull()?.message,
                    )
                }
            },
            onDismissed = {
                _state.value = AdRewardRepository.currentState().copy(syncing = false)
            },
        )
    }
}
