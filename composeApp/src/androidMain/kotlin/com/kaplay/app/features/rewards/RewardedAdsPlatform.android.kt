package com.kaplay.app.features.rewards

import com.kaplay.app.MainActivity
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsLoadOptions
import com.unity3d.ads.UnityAdsShowOptions

internal actual object RewardedAdsPlatform {
    private const val GAME_ID = "800397586"
    private const val PLACEMENT = "BP_Rewarded_Android"
    private const val INTERSTITIAL_PLACEMENT = "BP_Interstitial_Android"

    @Volatile private var initialized = false
    @Volatile private var loaded = false
    @Volatile private var interstitialLoaded = false

    actual fun bind() = Unit

    actual fun unbind() {
        loaded = false
        interstitialLoaded = false
    }

    actual fun initialize() {
        val activity = MainActivity.current ?: return
        if (initialized) return
        initialized = true
        UnityAds.initialize(
            activity,
            GAME_ID,
            false,
            object : IUnityAdsInitializationListener {
                override fun onInitializationComplete() = Unit
                override fun onInitializationFailed(
                    error: UnityAds.UnityAdsInitializationError?,
                    message: String?,
                ) {
                    initialized = false
                }
            },
        )
    }

    actual fun load(onLoaded: () -> Unit, onFailed: () -> Unit) {
        val activity = MainActivity.current
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onFailed()
            return
        }
        initialize()
        UnityAds.load(
            PLACEMENT,
            UnityAdsLoadOptions(),
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    loaded = placementId == PLACEMENT
                    if (loaded) onLoaded() else onFailed()
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?,
                ) {
                    loaded = false
                    onFailed()
                }
            },
        )
    }

    actual fun loadInterstitial(onLoaded: () -> Unit, onFailed: () -> Unit) {
        val activity = MainActivity.current
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onFailed()
            return
        }
        initialize()
        UnityAds.load(
            INTERSTITIAL_PLACEMENT,
            UnityAdsLoadOptions(),
            object : IUnityAdsLoadListener {
                override fun onUnityAdsAdLoaded(placementId: String?) {
                    interstitialLoaded = placementId == INTERSTITIAL_PLACEMENT
                    if (interstitialLoaded) onLoaded() else onFailed()
                }

                override fun onUnityAdsFailedToLoad(
                    placementId: String?,
                    error: UnityAds.UnityAdsLoadError?,
                    message: String?,
                ) {
                    interstitialLoaded = false
                    onFailed()
                }
            },
        )
    }

    actual fun showInterstitial(onDismissed: () -> Unit) {
        val activity = MainActivity.current
        if (activity == null || activity.isFinishing || activity.isDestroyed || !interstitialLoaded) {
            loadInterstitial(onLoaded = { showInterstitial(onDismissed) }, onFailed = onDismissed)
            return
        }
        interstitialLoaded = false
        UnityAds.show(
            activity,
            INTERSTITIAL_PLACEMENT,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowComplete(
                    placementId: String?,
                    state: UnityAds.UnityAdsShowCompletionState?,
                ) {
                    loadInterstitial(onLoaded = {}, onFailed = {})
                    onDismissed()
                }

                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?,
                ) {
                    interstitialLoaded = false
                    onDismissed()
                }

                override fun onUnityAdsShowStart(placementId: String?) = Unit
                override fun onUnityAdsShowClick(placementId: String?) = Unit
            },
        )
    }

    actual fun show(onCompleted: () -> Unit, onDismissed: () -> Unit) {
        val activity = MainActivity.current
        if (activity == null || activity.isFinishing || activity.isDestroyed || !loaded) {
            load(onLoaded = { show(onCompleted, onDismissed) }, onFailed = onDismissed)
            return
        }
        loaded = false
        UnityAds.show(
            activity,
            PLACEMENT,
            UnityAdsShowOptions(),
            object : IUnityAdsShowListener {
                override fun onUnityAdsShowComplete(
                    placementId: String?,
                    state: UnityAds.UnityAdsShowCompletionState?,
                ) {
                    load(onLoaded = {}, onFailed = {})
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        onCompleted()
                    } else {
                        onDismissed()
                    }
                }

                override fun onUnityAdsShowFailure(
                    placementId: String?,
                    error: UnityAds.UnityAdsShowError?,
                    message: String?,
                ) {
                    loaded = false
                    onDismissed()
                }

                override fun onUnityAdsShowStart(placementId: String?) = Unit
                override fun onUnityAdsShowClick(placementId: String?) = Unit
            },
        )
    }
}
