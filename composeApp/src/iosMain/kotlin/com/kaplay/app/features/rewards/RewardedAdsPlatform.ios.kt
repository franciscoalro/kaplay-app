package com.kaplay.app.features.rewards

internal actual object RewardedAdsPlatform {
    actual fun bind() = Unit
    actual fun unbind() = Unit
    actual fun initialize() = Unit
    actual fun load(onLoaded: () -> Unit, onFailed: () -> Unit) = onFailed()
    actual fun show(onCompleted: () -> Unit, onDismissed: () -> Unit) = onDismissed()
}
