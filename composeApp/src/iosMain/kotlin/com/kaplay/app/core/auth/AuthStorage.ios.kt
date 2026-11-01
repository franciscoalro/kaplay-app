package com.kaplay.app.core.auth

import platform.Foundation.NSUserDefaults

actual object AuthStorage {
    private const val KEY_ANONYMOUS_USER_ID = "anonymous_user_id"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    actual fun loadAnonymousUserId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(KEY_ANONYMOUS_USER_ID)

    actual fun saveAnonymousUserId(userId: String) {
        NSUserDefaults.standardUserDefaults.setObject(userId, forKey = KEY_ANONYMOUS_USER_ID)
    }

    actual fun clearAnonymousUserId() {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(KEY_ANONYMOUS_USER_ID)
    }

    actual fun isOnboardingCompleted(): Boolean =
        NSUserDefaults.standardUserDefaults.boolForKey(KEY_ONBOARDING_COMPLETED) || loadAnonymousUserId() != null

    actual fun markOnboardingCompleted() {
        NSUserDefaults.standardUserDefaults.setBool(true, forKey = KEY_ONBOARDING_COMPLETED)
    }
}
