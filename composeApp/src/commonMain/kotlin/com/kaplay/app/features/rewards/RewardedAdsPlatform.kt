package com.kaplay.app.features.rewards

internal expect object RewardedAdsPlatform {
    fun bind()
    fun unbind()
    fun initialize()
    fun load(onLoaded: () -> Unit, onFailed: () -> Unit)
    fun show(onCompleted: () -> Unit, onDismissed: () -> Unit)
}
