package com.kaplay.app.features.profiles

import com.kaplay.app.core.ui.KAPlayToastController
import kaplay.composeapp.generated.resources.Res
import kaplay.composeapp.generated.resources.profile_already_active
import org.jetbrains.compose.resources.getString

internal fun routeProfileSelection(
    profile: KAPlayProfile,
    isEditMode: Boolean,
    activeProfileIndex: Int? = null,
    onEditProfile: (KAPlayProfile) -> Unit,
    onActiveProfileSelected: (KAPlayProfile) -> Unit,
    onPinRequired: (KAPlayProfile) -> Unit,
    onProfileSelected: (KAPlayProfile) -> Unit,
) {
    when {
        isEditMode -> onEditProfile(profile)
        profile.profileIndex == activeProfileIndex -> onActiveProfileSelected(profile)
        profile.pinEnabled -> onPinRequired(profile)
        else -> onProfileSelected(profile)
    }
}

internal suspend fun showAlreadyActiveProfileToast(profile: KAPlayProfile) {
    KAPlayToastController.show(getString(Res.string.profile_already_active, profile.name))
}
